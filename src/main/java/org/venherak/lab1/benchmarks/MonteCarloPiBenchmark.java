package org.venherak.lab1.benchmarks;

import org.venherak.lab1.BenchmarkArguments;
import org.venherak.lab1.BenchmarkConfig;
import org.venherak.lab1.BenchmarkRunner.BenchmarkResult;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class MonteCarloPiBenchmark implements BenchmarkTask {
    private final int iterations;

    public MonteCarloPiBenchmark(String[] args) {
        this.iterations = BenchmarkArguments.intOption(args, "monteCarlo", 5_000_000);
    }

    @Override
    public String group() {
        return "CPU-bound";
    }

    @Override
    public String name() {
        return "Monte Carlo PI";
    }

    @Override
    public List<String> configurationLines() {
        return List.of("iterations=" + iterations);
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
        double pi = estimatePi(iterations, 0xBADC0FFEE0DDF00DL);
        long elapsed = System.nanoTime() - started;
        return new BenchmarkResult("Monte Carlo PI", false, 1, elapsed, String.format("%.8f", pi));
    }

    private BenchmarkResult benchmarkParallel(BenchmarkConfig config, int threadCount) throws Exception {
        long started = System.nanoTime();
        double pi = estimatePiParallel(iterations, threadCount);
        long elapsed = System.nanoTime() - started;
        return new BenchmarkResult("Monte Carlo PI", true, threadCount, elapsed, String.format("%.8f", pi));
    }

    private double estimatePi(int iterations, long seed) {
        SplittableRandom random = new SplittableRandom(seed);
        long insideCircle = 0;
        for (int i = 0; i < iterations; i++) {
            double x = random.nextDouble();
            double y = random.nextDouble();
            if (x * x + y * y <= 1.0) {
                insideCircle++;
            }
        }
        return 4.0 * insideCircle / iterations;
    }

    private double estimatePiParallel(int iterations, int threadCount) throws Exception {
        List<Callable<Long>> tasks = new ArrayList<>();
        int baseIterations = iterations / threadCount;
        int remainder = iterations % threadCount;

        for (int i = 0; i < threadCount; i++) {
            int chunk = baseIterations + (i < remainder ? 1 : 0);
            long seed = 0xA11CE5EEDL + i * 17L;
            tasks.add(() -> {
                SplittableRandom random = new SplittableRandom(seed);
                long insideCircle = 0;
                for (int j = 0; j < chunk; j++) {
                    double x = random.nextDouble();
                    double y = random.nextDouble();
                    if (x * x + y * y <= 1.0) {
                        insideCircle++;
                    }
                }
                return insideCircle;
            });
        }

        long insideCircle = 0;
        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            for (Future<Long> future : executor.invokeAll(tasks)) {
                insideCircle += future.get();
            }
        }
        return 4.0 * insideCircle / iterations;
    }
}
