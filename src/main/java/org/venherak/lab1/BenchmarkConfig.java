package org.venherak.lab1;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

public final class BenchmarkConfig {
    private final int[] threadCounts;

    private BenchmarkConfig(int[] threadCounts) {
        this.threadCounts = threadCounts;
    }

    public static BenchmarkConfig fromArgs(String[] args) {
        int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
        int[] threadCounts = defaultThreadCounts(cores);

        for (String arg : args) {
            if (!arg.startsWith("--") || !arg.contains("=")) {
                continue;
            }

            String[] parts = arg.substring(2).split("=", 2);
            String key = parts[0];
            String value = parts[1];

            if ("threads".equals(key)) {
                threadCounts = parseThreads(value);
            }
        }

        return new BenchmarkConfig(threadCounts);
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
}
