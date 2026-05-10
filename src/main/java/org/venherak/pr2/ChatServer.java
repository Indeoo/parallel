package org.venherak.pr2;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ChatServer implements AutoCloseable {
    private final String host;
    private final int port;
    private final ExecutorService clientExecutor = Executors.newCachedThreadPool();
    private final Map<String, ClientSession> activeUsers = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> groups = new ConcurrentHashMap<>();
    private final Map<String, List<ChatEnvelope>> offlineMessages = new ConcurrentHashMap<>();
    private final Map<String, String> userStates = new ConcurrentHashMap<>();
    private final List<String> eventLog = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ServerSocket serverSocket;
    private Thread acceptThread;

    public ChatServer(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running.set(true);
        log("SERVER", "Server started on " + host + ":" + port);
        acceptThread = new Thread(this::acceptLoop, "chat-accept");
        acceptThread.start();
    }

    public List<String> snapshotEventLog() {
        return List.copyOf(eventLog);
    }

    public List<String> activeUsers() {
        return activeUsers.keySet().stream().sorted().toList();
    }

    public List<String> knownUsers() {
        return userStates.keySet().stream().sorted().toList();
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket socket = serverSocket.accept();
                ClientSession session = new ClientSession(socket);
                session.startSender();
                clientExecutor.submit(() -> handleClient(session));
            } catch (IOException exception) {
                if (running.get()) {
                    log("SERVER", "Accept loop stopped: " + exception.getMessage());
                }
                break;
            }
        }
    }

    private void handleClient(ClientSession session) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(session.socket().getInputStream(), StandardCharsets.UTF_8))) {
            String registration = reader.readLine();
            if (!handleRegistration(session, registration)) {
                session.close();
                return;
            }

            String line;
            while ((line = reader.readLine()) != null) {
                handleCommand(session, line);
            }
        } catch (IOException exception) {
            if (session.username() != null) {
                log("SERVER", "Connection with " + session.username() + " closed: " + exception.getMessage());
            }
        } finally {
            disconnect(session);
        }
    }

    private boolean handleRegistration(ClientSession session, String registrationLine) {
        if (registrationLine == null) {
            return false;
        }
        String[] parts = registrationLine.split("\t", 2);
        if (parts.length != 2 || !"REGISTER".equals(parts[0])) {
            session.send("ERROR\tExpected REGISTER command");
            return false;
        }

        String username = parts[1].trim();
        if (username.isEmpty()) {
            session.send("ERROR\tUsername must not be empty");
            return false;
        }
        ClientSession existing = activeUsers.putIfAbsent(username, session);
        if (existing != null) {
            session.send("ERROR\tUsername already in use");
            return false;
        }

        session.setUsername(username);
        userStates.put(username, "ACTIVE");
        session.send("INFO\tRegistered as " + username);
        sendParticipantList(session);
        deliverOfflineMessages(session);
        broadcastSystem(username + " joined the chat", username);
        log("REGISTER", username + " connected");
        return true;
    }

    private void handleCommand(ClientSession session, String line) {
        String[] parts = line.split("\t", 5);
        String command = parts[0];
        switch (command) {
            case "LIST" -> sendParticipantList(session);
            case "BROADCAST" -> {
                if (parts.length < 2) {
                    session.send("ERROR\tBROADCAST requires text");
                } else {
                    broadcastMessage(session.username(), parts[1]);
                }
            }
            case "PRIVATE" -> {
                if (parts.length < 3) {
                    session.send("ERROR\tPRIVATE requires recipient and text");
                } else {
                    privateMessage(session.username(), parts[1], parts[2]);
                }
            }
            case "FILE" -> {
                if (parts.length < 4) {
                    session.send("ERROR\tFILE requires recipient, filename and content");
                } else {
                    fileMessage(session.username(), parts[1], parts[2], parts[3]);
                }
            }
            case "CREATE_GROUP" -> {
                if (parts.length < 3) {
                    session.send("ERROR\tCREATE_GROUP requires name and members");
                } else {
                    createGroup(session.username(), parts[1], parts[2]);
                }
            }
            case "GROUP" -> {
                if (parts.length < 3) {
                    session.send("ERROR\tGROUP requires name and text");
                } else {
                    groupMessage(session.username(), parts[1], parts[2]);
                }
            }
            case "QUIT" -> session.close();
            default -> session.send("ERROR\tUnknown command: " + command);
        }
    }

    private void sendParticipantList(ClientSession session) {
        String active = String.join(",", new TreeSet<>(activeUsers.keySet()));
        String inactive = userStates.entrySet().stream()
                .filter(entry -> !"ACTIVE".equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .sorted()
                .reduce((left, right) -> left + "," + right)
                .orElse("-");
        session.send("PARTICIPANTS\tactive=" + (active.isEmpty() ? "-" : active) + "\toffline=" + inactive);
    }

    private void broadcastMessage(String from, String text) {
        ChatEnvelope envelope = new ChatEnvelope(from, "ALL", "broadcast", text);
        activeUsers.values().forEach(session -> session.send(envelope.asServerEvent()));
        log("BROADCAST", from + " -> ALL: " + text);
    }

    private void privateMessage(String from, String to, String text) {
        ChatEnvelope envelope = new ChatEnvelope(from, to, "private", text);
        ClientSession recipient = activeUsers.get(to);
        if (recipient != null) {
            recipient.send(envelope.asServerEvent());
            ClientSession sender = activeUsers.get(from);
            if (sender != null) {
                sender.send("INFO\tDelivered private message to " + to);
            }
            log("PRIVATE", from + " -> " + to + ": " + text);
            return;
        }

        offlineMessages.computeIfAbsent(to, ignored -> new CopyOnWriteArrayList<>()).add(envelope);
        userStates.putIfAbsent(to, "OFFLINE");
        ClientSession sender = activeUsers.get(from);
        if (sender != null) {
            sender.send("INFO\tStored offline message for " + to);
        }
        log("OFFLINE", from + " -> " + to + ": " + text);
    }

    private void createGroup(String owner, String groupName, String membersCsv) {
        Set<String> members = ConcurrentHashMap.newKeySet();
        members.add(owner);
        for (String token : membersCsv.split(",")) {
            String member = token.trim();
            if (!member.isEmpty()) {
                members.add(member);
                userStates.putIfAbsent(member, "OFFLINE");
            }
        }
        groups.put(groupName, members);
        ClientSession ownerSession = activeUsers.get(owner);
        if (ownerSession != null) {
            ownerSession.send("INFO\tGroup " + groupName + " created with members " + String.join(",", new TreeSet<>(members)));
        }
        log("GROUP_CREATE", owner + " created " + groupName + " with " + String.join(",", new TreeSet<>(members)));
    }

    private void fileMessage(String from, String to, String fileName, String content) {
        ChatEnvelope envelope = new ChatEnvelope(from, to, "file:" + fileName, content);
        ClientSession recipient = activeUsers.get(to);
        if (recipient != null) {
            recipient.send(envelope.asServerEvent());
            ClientSession sender = activeUsers.get(from);
            if (sender != null) {
                sender.send("INFO\tDelivered file " + fileName + " to " + to);
            }
            log("FILE", from + " -> " + to + ": " + fileName);
            return;
        }

        offlineMessages.computeIfAbsent(to, ignored -> new CopyOnWriteArrayList<>()).add(envelope);
        userStates.putIfAbsent(to, "OFFLINE");
        ClientSession sender = activeUsers.get(from);
        if (sender != null) {
            sender.send("INFO\tStored offline file for " + to);
        }
        log("OFFLINE_FILE", from + " -> " + to + ": " + fileName);
    }

    private void groupMessage(String from, String groupName, String text) {
        Set<String> members = groups.get(groupName);
        if (members == null || !members.contains(from)) {
            ClientSession sender = activeUsers.get(from);
            if (sender != null) {
                sender.send("ERROR\tUnknown group or sender is not a member");
            }
            return;
        }

        for (String member : members) {
            if (member.equals(from)) {
                continue;
            }
            ChatEnvelope envelope = new ChatEnvelope(from, groupName + "/" + member, "group", text);
            ClientSession recipient = activeUsers.get(member);
            if (recipient != null) {
                recipient.send(envelope.asServerEvent());
            } else {
                offlineMessages.computeIfAbsent(member, ignored -> new CopyOnWriteArrayList<>()).add(envelope);
                userStates.putIfAbsent(member, "OFFLINE");
            }
        }
        ClientSession sender = activeUsers.get(from);
        if (sender != null) {
            sender.send("INFO\tGroup message sent to " + groupName);
        }
        log("GROUP", from + " -> " + groupName + ": " + text);
    }

    private void deliverOfflineMessages(ClientSession session) {
        List<ChatEnvelope> pending = offlineMessages.remove(session.username());
        if (pending == null || pending.isEmpty()) {
            session.send("INFO\tNo offline messages");
            return;
        }
        session.send("INFO\tDelivering " + pending.size() + " offline messages");
        for (ChatEnvelope envelope : pending) {
            session.send(envelope.asServerEvent());
        }
        log("OFFLINE_DELIVERY", session.username() + " received " + pending.size() + " offline messages");
    }

    private void broadcastSystem(String text, String excludedUser) {
        for (Map.Entry<String, ClientSession> entry : activeUsers.entrySet()) {
            if (!entry.getKey().equals(excludedUser)) {
                entry.getValue().send("INFO\t" + text);
            }
        }
    }

    private void disconnect(ClientSession session) {
        String username = session.username();
        if (username != null && activeUsers.remove(username, session)) {
            userStates.put(username, "OFFLINE@" + Instant.now());
            broadcastSystem(username + " disconnected", username);
            log("DISCONNECT", username + " disconnected");
        }
        session.close();
    }

    private void log(String category, String text) {
        eventLog.add("[" + category + "] " + text);
    }

    @Override
    public void close() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        }
        for (ClientSession session : new ArrayList<>(activeUsers.values())) {
            session.close();
        }
        clientExecutor.shutdownNow();
    }
}
