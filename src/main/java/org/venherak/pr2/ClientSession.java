package org.venherak.pr2;

import java.io.BufferedWriter;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

final class ClientSession {
    private final Socket socket;
    private final BufferedWriter writer;
    private final BlockingQueue<String> outboundQueue = new LinkedBlockingQueue<>();
    private final AtomicBoolean open = new AtomicBoolean(true);
    private volatile String username;
    private Thread senderThread;

    ClientSession(Socket socket) throws IOException {
        this.socket = socket;
        this.writer = new BufferedWriter(new java.io.OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    void setUsername(String username) {
        this.username = username;
    }

    String username() {
        return username;
    }

    Socket socket() {
        return socket;
    }

    void startSender() {
        senderThread = new Thread(this::senderLoop, "chat-sender-" + socket.getPort());
        senderThread.start();
    }

    void send(String line) {
        if (open.get()) {
            outboundQueue.offer(line);
        }
    }

    boolean isOpen() {
        return open.get();
    }

    void close() {
        if (!open.compareAndSet(true, false)) {
            return;
        }
        outboundQueue.offer("__CLOSE__");
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    private void senderLoop() {
        try {
            while (open.get()) {
                String line = outboundQueue.take();
                if ("__CLOSE__".equals(line)) {
                    break;
                }
                writer.write(line);
                writer.newLine();
                writer.flush();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (IOException ignored) {
        } finally {
            open.set(false);
            try {
                writer.close();
            } catch (IOException ignored) {
            }
        }
    }
}
