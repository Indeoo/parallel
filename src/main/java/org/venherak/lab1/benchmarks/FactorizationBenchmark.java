package org.venherak.lab1.benchmarks;

import org.venherak.lab1.BenchmarkArguments;
import org.venherak.lab1.BenchmarkConfig;
import org.venherak.lab1.BenchmarkRunner.BenchmarkResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class FactorizationBenchmark implements BenchmarkTask {
    private final long[] numbers;

    public FactorizationBenchmark(String[] args) {
        this.numbers = BenchmarkArguments.longArrayOption(
                args,
                "factors",
                new long[]{600_851_475_143L, 9_999_999_967L * 101L, 987_654_321_987L}
        );
    }

    @Override
    public String group() {
        return "CPU-bound";
    }

    @Override
    public String name() {
        return "Factorization";
    }

    @Override
    public List<String> configurationLines() {
        return List.of("numbers=" + formatNumbers());
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
        List<String> factors = new ArrayList<>();
        for (long number : numbers) {
            factors.add(number + "=" + factorize(number));
        }
        long elapsed = System.nanoTime() - started;
        return new BenchmarkResult("Factorization", false, 1, elapsed, String.join("; ", factors));
    }

    private BenchmarkResult benchmarkParallel(BenchmarkConfig config, int threadCount) throws Exception {
        long started = System.nanoTime();
        List<String> factors = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            List<Future<String>> futures = new ArrayList<>();
            for (long number : numbers) {
                futures.add(executor.submit(() -> number + "=" + factorize(number)));
            }
            for (Future<String> future : futures) {
                factors.add(future.get());
            }
        }
        long elapsed = System.nanoTime() - started;
        return new BenchmarkResult("Factorization", true, threadCount, elapsed, String.join("; ", factors));
    }

    private String factorize(long number) {
        List<Long> factors = new ArrayList<>();
        long remaining = number;

        while (remaining % 2 == 0) {
            factors.add(2L);
            remaining /= 2;
        }

        for (long divisor = 3; divisor * divisor <= remaining; divisor += 2) {
            while (remaining % divisor == 0) {
                factors.add(divisor);
                remaining /= divisor;
            }
        }

        if (remaining > 1) {
            factors.add(remaining);
        }

        return factors.toString();
    }

    private String formatNumbers() {
        List<String> values = new ArrayList<>();
        for (long number : numbers) {
            values.add(Long.toString(number));
        }
        return String.join(", ", values);
    }
}
