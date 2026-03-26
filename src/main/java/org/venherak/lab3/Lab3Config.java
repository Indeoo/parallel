package org.venherak.lab3;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

public final class Lab3Config {
    private final int[] threadCounts;
    private final int accountCount;
    private final int operationsPerThread;
    private final int ipcRounds;
    private final Path workingDirectory;

    private Lab3Config(
            int[] threadCounts,
            int accountCount,
            int operationsPerThread,
            int ipcRounds,
            Path workingDirectory
    ) {
        this.threadCounts = threadCounts;
        this.accountCount = accountCount;
        this.operationsPerThread = operationsPerThread;
        this.ipcRounds = ipcRounds;
        this.workingDirectory = workingDirectory;
    }

    public static Lab3Config fromArgs(String[] args) {
        int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
        int[] threadCounts = defaultThreadCounts(cores);
        int accountCount = 128;
        int operationsPerThread = 250;
        int ipcRounds = 200;

        for (String arg : args) {
            if (!arg.startsWith("--") || !arg.contains("=")) {
                continue;
            }
            String[] parts = arg.substring(2).split("=", 2);
            String key = parts[0];
            String value = parts[1];
            switch (key) {
                case "threads" -> threadCounts = parseThreads(value);
                case "accounts" -> accountCount = Integer.parseInt(value);
                case "opsPerThread" -> operationsPerThread = Integer.parseInt(value);
                case "ipcRounds" -> ipcRounds = Integer.parseInt(value);
                default -> {
                }
            }
        }

        return new Lab3Config(
                threadCounts,
                Math.max(100, accountCount),
                Math.max(1, operationsPerThread),
                Math.max(1, ipcRounds),
                Path.of("build", "generated", "lab3")
        );
    }

    private static int[] defaultThreadCounts(int cores) {
        Set<Integer> values = new LinkedHashSet<>();
        values.add(32);
        values.add(Math.max(64, cores * 4));
        values.add(256);
        values.add(1_200);
        return values.stream().mapToInt(Integer::intValue).distinct().sorted().toArray();
    }

    private static int[] parseThreads(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .mapToInt(Integer::parseInt)
                .filter(threadCount -> threadCount > 0)
                .distinct()
                .sorted()
                .toArray();
    }

    public int[] threadCounts() {
        return threadCounts;
    }

    public int accountCount() {
        return accountCount;
    }

    public int operationsPerThread() {
        return operationsPerThread;
    }

    public int ipcRounds() {
        return ipcRounds;
    }

    public Path workingDirectory() {
        return workingDirectory;
    }
}
