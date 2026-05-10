package org.venherak.pr2;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class ChatClient implements AutoCloseable {
    private final String username;
    private final String host;
    private final int port;
    private final int connectTimeoutMillis;
    private final Consumer<String> eventConsumer;
    private final List<String> transcript = new CopyOnWriteArrayList<>();
    private final AtomicBoolean open = new AtomicBoolean(false);
    private Socket socket;
    private BufferedWriter writer;
    private Thread receiverThread;

    public ChatClient(String username, String host, int port, int connectTimeoutMillis, Consumer<String> eventConsumer) {
        this.username = username;
        this.host = host;
        this.port = port;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.eventConsumer = eventConsumer;
    }

    public void connect() throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), connectTimeoutMillis);
        writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        open.set(true);
        receiverThread = new Thread(this::receiverLoop, "client-receiver-" + username);
        receiverThread.start();
        sendRaw("REGISTER\t" + username);
        emit("CONNECTED");
    }

    public void listParticipants() throws IOException {
        sendRaw("LIST");
    }

    public void broadcast(String text) throws IOException {
        sendRaw("BROADCAST\t" + text);
    }

    public void privateMessage(String to, String text) throws IOException {
        sendRaw("PRIVATE\t" + to + "\t" + text);
    }

    public void sendFile(String to, String fileName, String content) throws IOException {
        sendRaw("FILE\t" + to + "\t" + fileName + "\t" + content);
    }

    public void createGroup(String groupName, String membersCsv) throws IOException {
        sendRaw("CREATE_GROUP\t" + groupName + "\t" + membersCsv);
    }

    public void groupMessage(String groupName, String text) throws IOException {
        sendRaw("GROUP\t" + groupName + "\t" + text);
    }

    public List<String> transcript() {
        return List.copyOf(transcript);
    }

    public void disconnect() throws IOException {
        if (open.get()) {
            sendRaw("QUIT");
        }
        close();
    }

    private void receiverLoop() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                emit(line);
            }
        } catch (IOException exception) {
            if (open.get()) {
                emit("CONNECTION_CLOSED\t" + exception.getMessage());
            }
        } finally {
            open.set(false);
        }
    }

    private synchronized void sendRaw(String command) throws IOException {
        writer.write(command);
        writer.newLine();
        writer.flush();
        emit("SENT\t" + command);
    }

    private void emit(String event) {
        String line = "[" + username + "] " + event;
        transcript.add(line);
        eventConsumer.accept(line);
    }

    @Override
    public void close() {
        if (!open.compareAndSet(true, false)) {
            return;
        }
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
    }
}
