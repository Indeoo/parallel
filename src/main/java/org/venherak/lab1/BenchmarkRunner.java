package org.venherak.lab1;

import org.venherak.lab1.benchmarks.BenchmarkTask;
import org.venherak.lab1.benchmarks.FactorizationBenchmark;
import org.venherak.lab1.benchmarks.MatrixTransposeBenchmark;
import org.venherak.lab1.benchmarks.MonteCarloPiBenchmark;
import org.venherak.lab1.benchmarks.PrimeNumbersBenchmark;
import org.venherak.lab1.benchmarks.RecursiveWordCountBenchmark;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class BenchmarkRunner {
    private final BenchmarkConfig config;
    private final List<BenchmarkTask> tasks;

    public BenchmarkRunner(BenchmarkConfig config, String[] args) {
        this.config = config;
        this.tasks = List.of(
                new MonteCarloPiBenchmark(args),
                new FactorizationBenchmark(args),
                new PrimeNumbersBenchmark(args),
                new MatrixTransposeBenchmark(args),
                new RecursiveWordCountBenchmark(args, Path.of("build", "generated", "io-benchmark"))
        );
    }

    public void run() throws Exception {
        printHeader();
        String currentGroup = null;
        for (BenchmarkTask task : tasks) {
            if (!task.group().equals(currentGroup)) {
                currentGroup = task.group();
                printSection(currentGroup);
            }
            printTask(task);
            printResults(task.run(config));
        }
    }

    private void printHeader() {
        System.out.println("Laboratory work #1. Sequential and multithreaded benchmarks");
        System.out.println("Threads: " + formatThreads(config.threadCounts()));
        System.out.println();
    }

    private String formatThreads(int[] threadCounts) {
        List<String> values = new ArrayList<>();
        for (int threadCount : threadCounts) {
            values.add(Integer.toString(threadCount));
        }
        return String.join(", ", values);
    }

    private void printSection(String title) {
        System.out.println("=== " + title + " ===");
    }

    private void printTask(BenchmarkTask task) {
        System.out.println("Task: " + task.name());
        for (String line : task.configurationLines()) {
            System.out.println("  " + line);
        }
    }

    private void printResults(List<BenchmarkResult> results) {
        for (BenchmarkResult result : results) {
            String threads = result.parallel() ? Integer.toString(result.threadCount()) : "-";
            System.out.printf(
                    "%-28s mode=%-10s threads=%-3s time=%8.3f ms summary=%s%n",
                    result.name(),
                    result.parallel() ? "parallel" : "sequential",
                    threads,
                    result.elapsedNanos() / 1_000_000.0,
                    result.summary()
            );
        }
        System.out.println();
    }

    public record BenchmarkResult(String name, boolean parallel, int threadCount, long elapsedNanos, String summary) {
    }
}
