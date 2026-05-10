package org.venherak.pr1;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class WorkerPoolSimulation {
    private final int width;
    private final int height;
    private final int particleCount;
    private final int steps;
    private final int snapshotInterval;
    private final long seed;
    private final int workerCount;

    public WorkerPoolSimulation(
            int width,
            int height,
            int particleCount,
            int steps,
            int snapshotInterval,
            long seed,
            int workerCount
    ) {
        this.width = width;
        this.height = height;
        this.particleCount = particleCount;
        this.steps = steps;
        this.snapshotInterval = snapshotInterval;
        this.seed = seed;
        this.workerCount = Math.max(1, workerCount);
    }

    public SimulationResult run() throws Exception {
        int[] currentX = new int[particleCount];
        int[] currentY = new int[particleCount];
        SplittableRandom[] randoms = new SplittableRandom[particleCount];
        int[][] counts = initializeParticles(currentX, currentY, randoms);

        List<GridSnapshot> snapshots = new ArrayList<>();
        snapshots.add(new GridSnapshot(0, copyGrid(counts)));

        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        long start = System.nanoTime();
        try {
            for (int step = 1; step <= steps; step++) {
                int[] nextX = new int[particleCount];
                int[] nextY = new int[particleCount];
                List<Callable<WorkerChunkResult>> tasks = buildTasks(currentX, currentY, nextX, nextY, randoms);
                List<WorkerChunkResult> partialResults = new ArrayList<>();
                for (var future : executor.invokeAll(tasks)) {
                    partialResults.add(future.get());
                }

                counts = mergeCounts(partialResults);
                currentX = nextX;
                currentY = nextY;
                if (step % snapshotInterval == 0 || step == steps) {
                    snapshots.add(new GridSnapshot(step, copyGrid(counts)));
                }
            }
        } finally {
            executor.shutdownNow();
        }
        long elapsed = System.nanoTime() - start;

        return new SimulationResult(
                "worker-pool-" + workerCount,
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

    private List<Callable<WorkerChunkResult>> buildTasks(
            int[] currentX,
            int[] currentY,
            int[] nextX,
            int[] nextY,
            SplittableRandom[] randoms
    ) {
        int chunkSize = Math.max(1, (int) Math.ceil((double) particleCount / workerCount));
        List<Callable<WorkerChunkResult>> tasks = new ArrayList<>();
        for (int from = 0; from < particleCount; from += chunkSize) {
            int startInclusive = from;
            int endExclusive = Math.min(particleCount, from + chunkSize);
            tasks.add(() -> simulateChunk(startInclusive, endExclusive, currentX, currentY, nextX, nextY, randoms));
        }
        return tasks;
    }

    private WorkerChunkResult simulateChunk(
            int startInclusive,
            int endExclusive,
            int[] currentX,
            int[] currentY,
            int[] nextX,
            int[] nextY,
            SplittableRandom[] randoms
    ) {
        int[][] localCounts = new int[height][width];
        for (int index = startInclusive; index < endExclusive; index++) {
            Direction direction = Direction.fromIndex(randoms[index].nextInt(4));
            int newX = reflect(currentX[index] + direction.dx(), width);
            int newY = reflect(currentY[index] + direction.dy(), height);
            nextX[index] = newX;
            nextY[index] = newY;
            localCounts[newY][newX]++;
        }
        return new WorkerChunkResult(localCounts);
    }

    private int[][] initializeParticles(int[] currentX, int[] currentY, SplittableRandom[] randoms) {
        int[][] counts = new int[height][width];
        SplittableRandom random = new SplittableRandom(seed);
        for (int index = 0; index < particleCount; index++) {
            int x = random.nextInt(width);
            int y = random.nextInt(height);
            currentX[index] = x;
            currentY[index] = y;
            randoms[index] = new SplittableRandom(seed + index * 104_729L);
            counts[y][x]++;
        }
        return counts;
    }

    private int[][] mergeCounts(List<WorkerChunkResult> partialResults) {
        int[][] merged = new int[height][width];
        for (WorkerChunkResult partialResult : partialResults) {
            int[][] localCounts = partialResult.counts();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    merged[y][x] += localCounts[y][x];
                }
            }
        }
        return merged;
    }

    private int reflect(int coordinate, int limit) {
        if (coordinate < 0) {
            return 0;
        }
        if (coordinate >= limit) {
            return limit - 1;
        }
        return coordinate;
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

    private record WorkerChunkResult(int[][] counts) {
    }
}
