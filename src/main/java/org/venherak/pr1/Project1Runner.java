package org.venherak.pr1;

import java.time.Duration;

public final class Project1Runner {
    private final Project1Config config;

    public Project1Runner(Project1Config config) {
        this.config = config;
    }

    public void run() throws Exception {
        printHeader();

        RaceConditionDemo.RaceConditionResult raceResult =
                new RaceConditionDemo().run(config.particleCount(), config.raceIterations());
        DeadlockDemo.DeadlockResult deadlockResult =
                new DeadlockDemo().run(Duration.ofMillis(250));

        SimulationResult threadPerParticle =
                new ThreadPerParticleSimulation(
                        config.width(),
                        config.height(),
                        config.particleCount(),
                        config.steps(),
                        config.snapshotInterval(),
                        config.seed()
                ).run();

        int benchmarkSnapshotInterval = Math.max(1, config.benchmarkSteps() / 4);
        SimulationResult threadPerParticleBenchmark =
                new ThreadPerParticleSimulation(
                        config.width(),
                        config.height(),
                        config.benchmarkParticleCount(),
                        config.benchmarkSteps(),
                        benchmarkSnapshotInterval,
                        config.seed()
                ).run();

        SimulationResult workerPool =
                new WorkerPoolSimulation(
                        config.width(),
                        config.height(),
                        config.benchmarkParticleCount(),
                        config.benchmarkSteps(),
                        benchmarkSnapshotInterval,
                        config.seed(),
                        Math.max(2, Runtime.getRuntime().availableProcessors())
                ).run();

        printRaceSummary(raceResult);
        printDeadlockSummary(deadlockResult);
        printSimulationSummary(threadPerParticle);
        printSimulationSummary(threadPerParticleBenchmark);
        printSimulationSummary(workerPool);
        printSnapshots(threadPerParticle);
    }

    private void printHeader() {
        System.out.println("Навчальний проєкт №1. Моделювання броунівського руху");
        System.out.printf(
                "Grid=%dx%d, particles=%d, steps=%d, snapshotInterval=%d, seed=%d%n",
                config.width(),
                config.height(),
                config.particleCount(),
                config.steps(),
                config.snapshotInterval(),
                config.seed()
        );
        System.out.printf(
                "Benchmark particles=%d, benchmark steps=%d%n%n",
                config.benchmarkParticleCount(),
                config.benchmarkSteps()
        );
    }

    private void printRaceSummary(RaceConditionDemo.RaceConditionResult result) {
        System.out.println("=== Race condition demo ===");
        System.out.printf(
                "Threads=%d, iterations/thread=%d, expected=%d, unsafe=%d, safe=%d%n%n",
                result.threadCount(),
                result.iterationsPerThread(),
                result.expectedValue(),
                result.unsafeValue(),
                result.safeValue()
        );
    }

    private void printDeadlockSummary(DeadlockDemo.DeadlockResult result) {
        System.out.println("=== Deadlock demo ===");
        System.out.printf(
                "Deadlock detected=%s after %d ms, ordered locking completed=%s%n%n",
                result.deadlockDetected(),
                result.timeoutMillis(),
                result.orderedLockingCompleted()
        );
    }

    private void printSimulationSummary(SimulationResult result) {
        System.out.printf("=== Simulation: %s ===%n", result.mode());
        System.out.printf(
                "grid=%dx%d, particles=%d, steps=%d, snapshots=%d, finalParticles=%d, time=%.3f ms%n%n",
                result.width(),
                result.height(),
                result.particleCount(),
                result.steps(),
                result.snapshots().size(),
                result.finalParticleTotal(),
                result.elapsedMillis()
        );
    }

    private void printSnapshots(SimulationResult result) {
        System.out.println("=== Snapshots ===");
        for (GridSnapshot snapshot : result.snapshots()) {
            System.out.println(AsciiGridRenderer.render(snapshot));
        }
    }
}
