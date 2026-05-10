package org.venherak.pr2;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class Project2Runner {
    private final Project2Config config;

    public Project2Runner(Project2Config config) {
        this.config = config;
    }

    public void run() throws Exception {
        Project2Report report = executeDemo();
        printSummary(report);
    }

    public Project2Report executeDemo() throws Exception {
        List<String> transcript = new CopyOnWriteArrayList<>();
        long start = System.nanoTime();

        try (ChatServer server = new ChatServer(config.host(), config.port())) {
            server.start();
            sleep(200);

            ChatClient alice = new ChatClient("alice", config.host(), config.port(), config.connectTimeoutMillis(), transcript::add);
            ChatClient bob = new ChatClient("bob", config.host(), config.port(), config.connectTimeoutMillis(), transcript::add);
            ChatClient carol = new ChatClient("carol", config.host(), config.port(), config.connectTimeoutMillis(), transcript::add);

            try {
                alice.connect();
                sleep(config.actionDelayMillis());
                bob.connect();
                sleep(config.actionDelayMillis());
                carol.connect();
                sleep(config.actionDelayMillis());

                alice.listParticipants();
                sleep(config.actionDelayMillis());
                alice.broadcast("Hello everyone, this is alice.");
                sleep(config.actionDelayMillis());
                bob.privateMessage("alice", "Hi alice, bob is online.");
                sleep(config.actionDelayMillis());
                alice.sendFile("carol", "notes.txt", "Parallel chat demo notes.");
                sleep(config.actionDelayMillis());
                alice.createGroup("study", "bob,carol,dave");
                sleep(config.actionDelayMillis());
                carol.groupMessage("study", "Team sync at 18:00.");
                sleep(config.actionDelayMillis());

                bob.disconnect();
                sleep(config.actionDelayMillis());
                alice.privateMessage("bob", "Offline note for bob.");
                sleep(config.actionDelayMillis());
                alice.groupMessage("study", "Shared file uploaded to the group.");
                sleep(config.actionDelayMillis());

                ChatClient bobReconnect = new ChatClient(
                        "bob",
                        config.host(),
                        config.port(),
                        config.connectTimeoutMillis(),
                        transcript::add
                );
                try {
                    bobReconnect.connect();
                    sleep(config.actionDelayMillis());
                    bobReconnect.listParticipants();
                    sleep(config.actionDelayMillis());
                    bobReconnect.disconnect();
                    sleep(config.actionDelayMillis());
                } finally {
                    bobReconnect.close();
                }

                alice.disconnect();
                sleep(config.actionDelayMillis());
                carol.disconnect();
                sleep(config.actionDelayMillis());
            } finally {
                alice.close();
                bob.close();
                carol.close();
            }

            long elapsed = System.nanoTime() - start;
            return new Project2Report(elapsed, server.snapshotEventLog(), List.copyOf(new ArrayList<>(transcript)));
        }
    }

    private void printSummary(Project2Report report) {
        System.out.println("Навчальний проєкт №2. Розподілено-паралельна чат-система");
        System.out.printf("Server: %s:%d%n", config.host(), config.port());
        System.out.printf("Elapsed: %.3f ms%n%n", report.elapsedMillis());

        System.out.println("=== Server log ===");
        for (String line : report.serverLog()) {
            System.out.println(line);
        }
        System.out.println();

        System.out.println("=== Client transcript ===");
        for (String line : report.demoTranscript()) {
            System.out.println(line);
        }
    }

    private void sleep(int millis) throws InterruptedException {
        Thread.sleep(millis);
    }
}
