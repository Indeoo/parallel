package org.venherak.pr1;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public final class ThreadPerParticleSimulation {
    private final int width;
    private final int height;
    private final int particleCount;
    private final int steps;
    private final int snapshotInterval;
    private final long seed;

    public ThreadPerParticleSimulation(
            int width,
            int height,
            int particleCount,
            int steps,
            int snapshotInterval,
            long seed
    ) {
        this.width = width;
        this.height = height;
        this.particleCount = particleCount;
        this.steps = steps;
        this.snapshotInterval = snapshotInterval;
        this.seed = seed;
    }

    public SimulationResult run() throws InterruptedException {
        int[][] counts = new int[height][width];
        Particle[] particles = initializeParticles(counts);
        ReentrantLock[] cellLocks = new ReentrantLock[width * height];
        for (int index = 0; index < cellLocks.length; index++) {
            cellLocks[index] = new ReentrantLock();
        }

        List<GridSnapshot> snapshots = new ArrayList<>();
        snapshots.add(new GridSnapshot(0, copyGrid(counts)));

        AtomicInteger completedSteps = new AtomicInteger();
        CyclicBarrier barrier = new CyclicBarrier(particleCount, () -> {
            int step = completedSteps.incrementAndGet();
            if (step % snapshotInterval == 0 || step == steps) {
                snapshots.add(new GridSnapshot(step, copyGrid(counts)));
            }
        });

        List<Thread> threads = new ArrayList<>();
        long start = System.nanoTime();
        for (int particleIndex = 0; particleIndex < particleCount; particleIndex++) {
            final int index = particleIndex;
            Thread thread = new Thread(() -> moveParticle(index, particles, counts, cellLocks, barrier), "particle-" + index);
            threads.add(thread);
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }
        long elapsed = System.nanoTime() - start;

        return new SimulationResult(
                "thread-per-particle",
                width,
                height,
                particleCount,
                steps,
                snapshotInterval,
                elapsed,
                List.copyOf(snapshots),
                sumGrid(counts)
        );
    }

    private void moveParticle(
            int particleIndex,
            Particle[] particles,
            int[][] counts,
            ReentrantLock[] cellLocks,
            CyclicBarrier barrier
    ) {
        SplittableRandom random = new SplittableRandom(seed + particleIndex * 104_729L);
        Particle particle = particles[particleIndex];

        for (int step = 0; step < steps; step++) {
            Direction direction = Direction.fromIndex(random.nextInt(4));
            int nextX = reflect(particle.x + direction.dx(), width);
            int nextY = reflect(particle.y + direction.dy(), height);
            int currentIndex = indexOf(particle.x, particle.y);
            int nextIndex = indexOf(nextX, nextY);

            ReentrantLock firstLock = cellLocks[Math.min(currentIndex, nextIndex)];
            ReentrantLock secondLock = cellLocks[Math.max(currentIndex, nextIndex)];

            firstLock.lock();
            try {
                if (secondLock != firstLock) {
                    secondLock.lock();
                }
                try {
                    if (currentIndex != nextIndex) {
                        counts[particle.y][particle.x]--;
                        counts[nextY][nextX]++;
                        particle.x = nextX;
                        particle.y = nextY;
                    }
                } finally {
                    if (secondLock != firstLock) {
                        secondLock.unlock();
                    }
                }
            } finally {
                firstLock.unlock();
            }

            awaitBarrier(barrier);
        }
    }

    private void awaitBarrier(CyclicBarrier barrier) {
        try {
            barrier.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Barrier interrupted", exception);
        } catch (BrokenBarrierException exception) {
            throw new IllegalStateException("Barrier broken", exception);
        }
    }

    private Particle[] initializeParticles(int[][] counts) {
        Particle[] particles = new Particle[particleCount];
        SplittableRandom random = new SplittableRandom(seed);
        for (int index = 0; index < particleCount; index++) {
            int x = random.nextInt(width);
            int y = random.nextInt(height);
            counts[y][x]++;
            particles[index] = new Particle(x, y);
        }
        return particles;
    }

    private int reflect(int coordinate, int limit) {
        if (coordinate < 0 || coordinate >= limit) {
            return Math.max(0, Math.min(limit - 1, coordinate - Integer.signum(coordinate)));
        }
        return coordinate;
    }

    private int indexOf(int x, int y) {
        return y * width + x;
    }

    private int[][] copyGrid(int[][] grid) {
        int[][] copy = new int[grid.length][];
        for (int row = 0; row < grid.length; row++) {
            copy[row] = grid[row].clone();
        }
        return copy;
    }

    private int sumGrid(int[][] grid) {
        int total = 0;
        for (int y = 0; y < grid.length; y++) {
            for (int x = 0; x < grid[y].length; x++) {
                total += grid[y][x];
            }
        }
        return total;
    }

    private static final class Particle {
        private int x;
        private int y;

        private Particle(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }
}
