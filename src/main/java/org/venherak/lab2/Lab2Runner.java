package org.venherak.lab2;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class Lab2Runner {
    private final Lab2Config config;

    public Lab2Runner(Lab2Config config) {
        this.config = config;
    }

    public void run() throws Exception {
        printHeader();

        IndependentTaskBenchmarks independentTaskBenchmarks = new IndependentTaskBenchmarks(config);
        List<BenchmarkResult> task1Results = independentTaskBenchmarks.run();
        printSection("Task 1. Map-Reduce, Fork-Join, Worker Pool");
        printResults(task1Results);
        printBestPerTask(task1Results);

        TransactionPatternBenchmarks transactionPatternBenchmarks = new TransactionPatternBenchmarks(config);
        List<BenchmarkResult> task2Results = transactionPatternBenchmarks.run();
        printSection("Task 2. Pipeline, Producer-Consumer");
        printResults(task2Results);
        printBestPerTask(task2Results);
    }

    private void printHeader() {
        System.out.println("Laboratory work #2. Parallel programming patterns");
        System.out.println("Threads: " + join(config.threadCounts()));
        System.out.println("HTML documents: " + config.htmlDocumentCount());
        System.out.println("Numbers in array: " + config.numberCount());
        System.out.println("Matrix size: " + config.matrixSize() + " x " + config.matrixSize());
        System.out.println("Transactions: " + config.transactionCount());
        System.out.println();
    }

    private void printSection(String title) {
        System.out.println("=== " + title + " ===");
    }

    private void printResults(List<BenchmarkResult> results) {
        for (BenchmarkResult result : results) {
            String threads = result.threads() <= 0 ? "-" : Integer.toString(result.threads());
            System.out.printf(
                    "%-32s pattern=%-18s threads=%-3s time=%9.3f ms summary=%s%n",
                    result.taskName(),
                    result.pattern(),
                    threads,
                    result.elapsedMillis(),
                    result.summary()
            );
        }
        System.out.println();
    }

    private void printBestPerTask(List<BenchmarkResult> results) {
        List<String> taskNames = results.stream().map(BenchmarkResult::taskName).distinct().toList();
        for (String taskName : taskNames) {
            BenchmarkResult best = results.stream()
                    .filter(result -> result.taskName().equals(taskName))
                    .min(Comparator.comparingLong(BenchmarkResult::elapsedNanos))
                    .orElseThrow();
            System.out.printf(
                    "Best for %-24s -> %s (%s threads=%s, %.3f ms)%n",
                    taskName,
                    best.pattern(),
                    best.pattern(),
                    best.threads() <= 0 ? "-" : best.threads(),
                    best.elapsedMillis()
            );
        }
        System.out.println();
    }

    private String join(int[] values) {
        List<String> tokens = new ArrayList<>();
        for (int value : values) {
            tokens.add(Integer.toString(value));
        }
        return String.join(", ", tokens);
    }
}
