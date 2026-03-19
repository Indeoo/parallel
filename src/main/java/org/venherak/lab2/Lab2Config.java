package org.venherak.lab2;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

public final class Lab2Config {
    private final int[] threadCounts;
    private final int htmlDocumentCount;
    private final int numberCount;
    private final int matrixSize;
    private final int transactionCount;
    private final Path workingDirectory;

    private Lab2Config(
            int[] threadCounts,
            int htmlDocumentCount,
            int numberCount,
            int matrixSize,
            int transactionCount,
            Path workingDirectory
    ) {
        this.threadCounts = threadCounts;
        this.htmlDocumentCount = htmlDocumentCount;
        this.numberCount = numberCount;
        this.matrixSize = matrixSize;
        this.transactionCount = transactionCount;
        this.workingDirectory = workingDirectory;
    }

    public static Lab2Config fromArgs(String[] args) {
        int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
        int[] threadCounts = defaultThreadCounts(cores);
        int htmlDocumentCount = 1_200;
        int numberCount = 1_200_000;
        int matrixSize = 192;
        int transactionCount = 250_000;

        for (String arg : args) {
            if (!arg.startsWith("--") || !arg.contains("=")) {
                continue;
            }
            String[] parts = arg.substring(2).split("=", 2);
            String key = parts[0];
            String value = parts[1];
            switch (key) {
                case "threads" -> threadCounts = parseThreads(value);
                case "htmlDocs" -> htmlDocumentCount = Integer.parseInt(value);
                case "numbers" -> numberCount = Integer.parseInt(value);
                case "matrixSize" -> matrixSize = Integer.parseInt(value);
                case "transactions" -> transactionCount = Integer.parseInt(value);
                default -> {
                }
            }
        }

        return new Lab2Config(
                threadCounts,
                Math.max(1, htmlDocumentCount),
                Math.max(1, numberCount),
                Math.max(1, matrixSize),
                Math.max(1, transactionCount),
                Path.of("build", "generated", "lab2")
        );
    }

    private static int[] defaultThreadCounts(int cores) {
        Set<Integer> values = new LinkedHashSet<>();
        values.add(1);
        values.add(Math.max(2, cores / 2));
        values.add(cores);
        values.add(cores * 2);
        return values.stream().mapToInt(Integer::intValue).sorted().toArray();
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

    public int htmlDocumentCount() {
        return htmlDocumentCount;
    }

    public int numberCount() {
        return numberCount;
    }

    public int matrixSize() {
        return matrixSize;
    }

    public int transactionCount() {
        return transactionCount;
    }

    public Path workingDirectory() {
        return workingDirectory;
    }
}
