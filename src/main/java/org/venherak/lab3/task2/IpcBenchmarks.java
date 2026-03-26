package org.venherak.lab3.task2;

import org.venherak.lab3.BenchmarkResult;
import org.venherak.lab3.Lab3Config;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

public final class IpcBenchmarks {
    private final Lab3Config config;

    public IpcBenchmarks(Lab3Config config) {
        this.config = config;
    }

    public List<BenchmarkResult> run() throws Exception {
        Files.createDirectories(config.workingDirectory());
        List<BenchmarkResult> results = new ArrayList<>();
        results.add(runJavaStdio());
        results.add(runJavaSocket());
        results.add(runPythonFileExchange());
        return results;
    }

    private BenchmarkResult runJavaStdio() throws Exception {
        Process process = startJavaProcess("stdio");
        try (
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))
        ) {
            SplittableRandom random = new SplittableRandom(11L);
            long checksum = 0;
            long startedAt = System.nanoTime();
            for (int round = 0; round < config.ipcRounds(); round++) {
                int value = random.nextInt(1_000_000);
                writer.write(Integer.toString(value));
                writer.newLine();
                writer.flush();
                int echoed = Integer.parseInt(reader.readLine());
                checksum += echoed;
            }
            long elapsed = System.nanoTime() - startedAt;
            process.getOutputStream().close();
            return new BenchmarkResult(
                    "ipc",
                    "java-stdio-message-passing",
                    0,
                    elapsed,
                    "env=java-java, rounds=" + config.ipcRounds() + ", checksum=" + checksum
            );
        } finally {
            awaitExit(process, "stdio");
        }
    }

    private BenchmarkResult runJavaSocket() throws Exception {
        int port;
        try (ServerSocket probe = new ServerSocket(0)) {
            port = probe.getLocalPort();
        }

        Process process = startJavaProcess("socket", Integer.toString(port));
        try (
                BufferedReader processReader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))
        ) {
            String ready = processReader.readLine();
            if (!"READY".equals(ready)) {
                throw new IllegalStateException("Socket worker did not start correctly: " + ready);
            }
            try (
                    Socket socket = new Socket("127.0.0.1", port);
                    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            ) {
                SplittableRandom random = new SplittableRandom(19L);
                long checksum = 0;
                long startedAt = System.nanoTime();
                for (int round = 0; round < config.ipcRounds(); round++) {
                    int value = random.nextInt(1_000_000);
                    writer.write(Integer.toString(value));
                    writer.newLine();
                    writer.flush();
                    int echoed = Integer.parseInt(reader.readLine());
                    checksum += echoed;
                }
                long elapsed = System.nanoTime() - startedAt;
                return new BenchmarkResult(
                        "ipc",
                        "java-socket-message-passing",
                        0,
                        elapsed,
                        "env=java-java, rounds=" + config.ipcRounds() + ", checksum=" + checksum
                );
            }
        } finally {
            awaitExit(process, "socket");
        }
    }

    private BenchmarkResult runPythonFileExchange() throws Exception {
        Path exchangeDirectory = config.workingDirectory().resolve("python-file-exchange");
        Files.createDirectories(exchangeDirectory);
        Path requestFile = exchangeDirectory.resolve("request.txt");
        Path responseFile = exchangeDirectory.resolve("response.txt");
        Files.deleteIfExists(requestFile);
        Files.deleteIfExists(responseFile);
        Process process = new ProcessBuilder(
                "python3",
                "src/main/python/lab3_file_exchange_worker.py",
                requestFile.toAbsolutePath().toString(),
                responseFile.toAbsolutePath().toString()
        )
                .directory(projectRoot().toFile())
                .redirectErrorStream(true)
                .start();

        try {
            SplittableRandom random = new SplittableRandom(23L);
            long checksum = 0;
            long startedAt = System.nanoTime();
            for (int round = 0; round < config.ipcRounds(); round++) {
                int value = random.nextInt(1_000_000);
                String payload = round + ":" + value;
                Files.writeString(
                        requestFile,
                        payload,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING
                );
                String echoed = waitForResponse(responseFile, payload, TimeUnit.SECONDS.toNanos(2));
                checksum += Integer.parseInt(echoed.substring(echoed.indexOf(':') + 1));
            }
            Files.writeString(
                    requestFile,
                    "STOP",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            long elapsed = System.nanoTime() - startedAt;
            return new BenchmarkResult(
                    "ipc",
                    "python-file-exchange",
                    0,
                    elapsed,
                    "env=java-python, rounds=" + config.ipcRounds() + ", checksum=" + checksum
            );
        } finally {
            awaitExit(process, "python file exchange");
        }
    }

    private Process startJavaProcess(String... args) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(JavaEchoWorker.class.getName());
        command.addAll(List.of(args));
        return new ProcessBuilder(command)
                .directory(projectRoot().toFile())
                .redirectErrorStream(true)
                .start();
    }

    private Path projectRoot() {
        return Path.of("").toAbsolutePath().normalize();
    }

    private void awaitExit(Process process, String label) throws Exception {
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("Timed out while waiting for " + label + " worker");
        }
        if (process.exitValue() != 0) {
            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!builder.isEmpty()) {
                        builder.append(System.lineSeparator());
                    }
                    builder.append(line);
                }
                output = builder.toString();
            }
            throw new IllegalStateException("Worker failed for " + label + ": " + output);
        }
    }

    private String waitForResponse(Path responseFile, String expectedPayload, long timeoutNanos) throws Exception {
        long deadline = System.nanoTime() + timeoutNanos;
        while (System.nanoTime() < deadline) {
            if (Files.exists(responseFile)) {
                String response = Files.readString(responseFile, StandardCharsets.UTF_8).trim();
                if (expectedPayload.equals(response)) {
                    return response;
                }
            }
            Thread.sleep(2L);
        }
        throw new IllegalStateException("Timeout while waiting for python file response");
    }
}
