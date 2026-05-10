package org.venherak.pr1;

import java.nio.file.Path;

public final class Project1Config {
    private final int width;
    private final int height;
    private final int particleCount;
    private final int steps;
    private final int snapshotInterval;
    private final long seed;
    private final int raceIterations;
    private final int benchmarkParticleCount;
    private final int benchmarkSteps;
    private final Path resultDirectory;

    private Project1Config(
            int width,
            int height,
            int particleCount,
            int steps,
            int snapshotInterval,
            long seed,
            int raceIterations,
            int benchmarkParticleCount,
            int benchmarkSteps,
            Path resultDirectory
    ) {
        this.width = width;
        this.height = height;
        this.particleCount = particleCount;
        this.steps = steps;
        this.snapshotInterval = snapshotInterval;
        this.seed = seed;
        this.raceIterations = raceIterations;
        this.benchmarkParticleCount = benchmarkParticleCount;
        this.benchmarkSteps = benchmarkSteps;
        this.resultDirectory = resultDirectory;
    }

    public static Project1Config fromArgs(String[] args) {
        int width = 20;
        int height = 12;
        int particleCount = 48;
        int steps = 40;
        int snapshotInterval = 10;
        long seed = 20_260_510L;
        int raceIterations = 20_000;
        int benchmarkParticleCount = 240;
        int benchmarkSteps = 120;
        Path resultDirectory = Path.of("result");

        for (String arg : args) {
            if (!arg.startsWith("--") || !arg.contains("=")) {
                continue;
            }
            String[] parts = arg.substring(2).split("=", 2);
            String key = parts[0];
            String value = parts[1];
            switch (key) {
                case "width" -> width = Integer.parseInt(value);
                case "height" -> height = Integer.parseInt(value);
                case "particles" -> particleCount = Integer.parseInt(value);
                case "steps" -> steps = Integer.parseInt(value);
                case "snapshotInterval" -> snapshotInterval = Integer.parseInt(value);
                case "seed" -> seed = Long.parseLong(value);
                case "raceIterations" -> raceIterations = Integer.parseInt(value);
                case "benchmarkParticles" -> benchmarkParticleCount = Integer.parseInt(value);
                case "benchmarkSteps" -> benchmarkSteps = Integer.parseInt(value);
                case "resultDir" -> resultDirectory = Path.of(value);
                default -> {
                }
            }
        }

        int normalizedWidth = Math.max(4, width);
        int normalizedHeight = Math.max(4, height);
        int normalizedParticleCount = Math.max(2, particleCount);
        int normalizedSteps = Math.max(1, steps);
        int normalizedSnapshotInterval = Math.max(1, Math.min(snapshotInterval, normalizedSteps));
        int normalizedRaceIterations = Math.max(1_000, raceIterations);
        int normalizedBenchmarkParticleCount = Math.max(normalizedParticleCount, benchmarkParticleCount);
        int normalizedBenchmarkSteps = Math.max(normalizedSteps, benchmarkSteps);

        return new Project1Config(
                normalizedWidth,
                normalizedHeight,
                normalizedParticleCount,
                normalizedSteps,
                normalizedSnapshotInterval,
                seed,
                normalizedRaceIterations,
                normalizedBenchmarkParticleCount,
                normalizedBenchmarkSteps,
                resultDirectory
        );
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int particleCount() {
        return particleCount;
    }

    public int steps() {
        return steps;
    }

    public int snapshotInterval() {
        return snapshotInterval;
    }

    public long seed() {
        return seed;
    }

    public int raceIterations() {
        return raceIterations;
    }

    public int benchmarkParticleCount() {
        return benchmarkParticleCount;
    }

    public int benchmarkSteps() {
        return benchmarkSteps;
    }

    public Path resultDirectory() {
        return resultDirectory;
    }
}
