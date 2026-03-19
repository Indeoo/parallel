package org.venherak.lab1.benchmarks;

import org.venherak.lab1.BenchmarkArguments;
import org.venherak.lab1.BenchmarkConfig;
import org.venherak.lab1.BenchmarkRunner.BenchmarkResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class MatrixTransposeBenchmark implements BenchmarkTask {
    private final int size;
    private final int blockSize;

    public MatrixTransposeBenchmark(String[] args) {
        long maxMemory = Runtime.getRuntime().maxMemory();
        int defaultMatrixSize = maxMemory >= 1_400_000_000L ? 10_000 : 4_096;
        this.size = BenchmarkArguments.intOption(args, "matrixSize", defaultMatrixSize);
        this.blockSize = BenchmarkArguments.intOption(args, "matrixBlock", 64);
    }

    @Override
    public String group() {
        return "Memory-bound";
    }

    @Override
    public String name() {
        return "Matrix transpose";
    }

    @Override
    public List<String> configurationLines() {
        return List.of("size=" + size + "x" + size, "blockSize=" + blockSize);
    }

    @Override
    public List<BenchmarkResult> run(BenchmarkConfig config) throws Exception {
        short[] source = createMatrix(size);
        List<BenchmarkResult> results = new ArrayList<>();
        results.add(benchmarkSequential(config, source));
        for (int threadCount : config.threadCounts()) {
            results.add(benchmarkParallel(config, source, threadCount));
        }
        return results;
    }

    private BenchmarkResult benchmarkSequential(BenchmarkConfig config, short[] source) {
        short[] target = new short[source.length];
        long started = System.nanoTime();
        transposeSequential(source, target, size, blockSize);
        long elapsed = System.nanoTime() - started;
        return new BenchmarkResult("Matrix transpose", false, 1, elapsed, "checksum=" + checksum(target));
    }

    private BenchmarkResult benchmarkParallel(BenchmarkConfig config, short[] source, int threadCount) throws Exception {
        short[] target = new short[source.length];
        long started = System.nanoTime();
        transposeParallel(source, target, size, blockSize, threadCount);
        long elapsed = System.nanoTime() - started;
        return new BenchmarkResult("Matrix transpose", true, threadCount, elapsed, "checksum=" + checksum(target));
    }

    private short[] createMatrix(int size) {
        short[] matrix = new short[size * size];
        for (int row = 0; row < size; row++) {
            int rowOffset = row * size;
            for (int column = 0; column < size; column++) {
                matrix[rowOffset + column] = (short) ((row * 31 + column * 17) & 0x7FFF);
            }
        }
        return matrix;
    }

    private void transposeSequential(short[] source, short[] target, int size, int blockSize) {
        for (int rowBlock = 0; rowBlock < size; rowBlock += blockSize) {
            int rowEnd = Math.min(size, rowBlock + blockSize);
            for (int columnBlock = 0; columnBlock < size; columnBlock += blockSize) {
                int columnEnd = Math.min(size, columnBlock + blockSize);
                for (int row = rowBlock; row < rowEnd; row++) {
                    int rowOffset = row * size;
                    for (int column = columnBlock; column < columnEnd; column++) {
                        target[column * size + row] = source[rowOffset + column];
                    }
                }
            }
        }
    }

    private void transposeParallel(short[] source, short[] target, int size, int blockSize, int threadCount)
            throws InterruptedException, ExecutionException {
        int blockRows = (size + blockSize - 1) / blockSize;
        int blocksPerTask = Math.max(1, (blockRows + threadCount - 1) / threadCount);
        List<Runnable> tasks = new ArrayList<>();

        for (int rowBlockIndex = 0; rowBlockIndex < blockRows; rowBlockIndex += blocksPerTask) {
            int startRowBlock = rowBlockIndex * blockSize;
            int endRowBlock = Math.min(size, (rowBlockIndex + blocksPerTask) * blockSize);
            tasks.add(() -> transposeRange(source, target, size, blockSize, startRowBlock, endRowBlock));
        }

        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            List<Future<?>> futures = new ArrayList<>();
            for (Runnable task : tasks) {
                futures.add(executor.submit(task));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        }
    }

    private void transposeRange(short[] source, short[] target, int size, int blockSize, int startRow, int endRow) {
        for (int rowBlock = startRow; rowBlock < endRow; rowBlock += blockSize) {
            int rowEnd = Math.min(size, rowBlock + blockSize);
            for (int columnBlock = 0; columnBlock < size; columnBlock += blockSize) {
                int columnEnd = Math.min(size, columnBlock + blockSize);
                for (int row = rowBlock; row < rowEnd; row++) {
                    int rowOffset = row * size;
                    for (int column = columnBlock; column < columnEnd; column++) {
                        target[column * size + row] = source[rowOffset + column];
                    }
                }
            }
        }
    }

    private long checksum(short[] matrix) {
        long checksum = 0;
        int step = Math.max(1, matrix.length / 4_096);
        for (int index = 0; index < matrix.length; index += step) {
            checksum = checksum * 31 + matrix[index];
        }
        return checksum;
    }
}
