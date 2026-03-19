package org.venherak.lab1.benchmarks;

import org.venherak.lab1.BenchmarkArguments;
import org.venherak.lab1.BenchmarkConfig;
import org.venherak.lab1.BenchmarkRunner.BenchmarkResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class PrimeNumbersBenchmark implements BenchmarkTask {
    private final int limit;

    public PrimeNumbersBenchmark(String[] args) {
        this.limit = BenchmarkArguments.intOption(args, "primeLimit", 250_000);
    }

    @Override
    public String group() {
        return "CPU-bound";
    }

    @Override
    public String name() {
        return "Prime numbers";
    }

    @Override
    public List<String> configurationLines() {
        return List.of("limit=" + limit);
    }

    @Override
    public List<BenchmarkResult> run(BenchmarkConfig config) throws Exception {
        List<BenchmarkResult> results = new ArrayList<>();
        results.add(benchmarkSequential(config));
        for (int threadCount : config.threadCounts()) {
            results.add(benchmarkParallel(config, threadCount));
        }
        return results;
    }

    private BenchmarkResult benchmarkSequential(BenchmarkConfig config) {
        long started = System.nanoTime();
        int count = countPrimes(2, limit);
        long elapsed = System.nanoTime() - started;
        return new BenchmarkResult("Prime numbers", false, 1, elapsed, "count=" + count);
    }

    private BenchmarkResult benchmarkParallel(BenchmarkConfig config, int threadCount) throws Exception {
        long started = System.nanoTime();
        int count = countPrimesParallel(limit, threadCount);
        long elapsed = System.nanoTime() - started;
        return new BenchmarkResult("Prime numbers", true, threadCount, elapsed, "count=" + count);
    }

    private int countPrimes(int startInclusive, int endInclusive) {
        int count = 0;
        for (int candidate = Math.max(2, startInclusive); candidate <= endInclusive; candidate++) {
            if (isPrime(candidate)) {
                count++;
            }
        }
        return count;
    }

    private int countPrimesParallel(int limit, int threadCount) throws InterruptedException, ExecutionException {
        int numbers = Math.max(0, limit - 1);
        int chunkSize = Math.max(1, (numbers + threadCount - 1) / threadCount);
        List<Callable<Integer>> tasks = new ArrayList<>();

        for (int start = 2; start <= limit; start += chunkSize) {
            int rangeStart = start;
            int rangeEnd = Math.min(limit, start + chunkSize - 1);
            tasks.add(() -> countPrimes(rangeStart, rangeEnd));
        }

        int total = 0;
        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            for (Future<Integer> future : executor.invokeAll(tasks)) {
                total += future.get();
            }
        }
        return total;
    }

    private boolean isPrime(int candidate) {
        if (candidate < 2) {
            return false;
        }
        if (candidate == 2) {
            return true;
        }
        if (candidate % 2 == 0) {
            return false;
        }

        int limit = (int) Math.sqrt(candidate);
        for (int divisor = 3; divisor <= limit; divisor += 2) {
            if (candidate % divisor == 0) {
                return false;
            }
        }
        return true;
    }
}
