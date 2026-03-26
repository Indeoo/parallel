package org.venherak.lab3;

import org.venherak.lab3.task1.BankTransferBenchmarks;
import org.venherak.lab3.task2.IpcBenchmarks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class Lab3Runner {
    private final Lab3Config config;

    public Lab3Runner(Lab3Config config) {
        this.config = config;
    }

    public void run() throws Exception {
        printHeader();

        BankTransferBenchmarks bankTransferBenchmarks = new BankTransferBenchmarks(config);
        List<BenchmarkResult> task1Results = bankTransferBenchmarks.run();
        printSection("Task 1. Deadlock and Race Condition");
        printResults(task1Results);
        printBestPerTask(task1Results);

        IpcBenchmarks ipcBenchmarks = new IpcBenchmarks(config);
        List<BenchmarkResult> task2Results = ipcBenchmarks.run();
        printSection("Task 2. IPC across processes");
        printResults(task2Results);
        printBestPerTask(task2Results);
    }

    private void printHeader() {
        System.out.println("Laboratory work #3. Synchronization and inter-process communication");
        System.out.println("Threads: " + join(config.threadCounts()));
        System.out.println("Accounts: " + config.accountCount());
        System.out.println("Transfers per thread: " + config.operationsPerThread());
        System.out.println("IPC round-trips: " + config.ipcRounds());
        System.out.println();
    }

    private void printSection(String title) {
        System.out.println("=== " + title + " ===");
    }

    private void printResults(List<BenchmarkResult> results) {
        for (BenchmarkResult result : results) {
            String threads = result.threads() <= 0 ? "-" : Integer.toString(result.threads());
            System.out.printf(
                    "%-28s mode=%-24s threads=%-5s time=%9.3f ms summary=%s%n",
                    result.taskName(),
                    result.mode(),
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
                    "Best for %-20s -> %s (threads=%s, %.3f ms)%n",
                    taskName,
                    best.mode(),
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
