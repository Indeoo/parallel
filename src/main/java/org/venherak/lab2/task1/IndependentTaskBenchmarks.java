package org.venherak.lab2.task1;

import org.venherak.lab2.BenchmarkResult;
import org.venherak.lab2.Lab2Config;
import org.venherak.lab2.task1.generator.Task1InputGenerator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.RecursiveTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

public final class IndependentTaskBenchmarks {
    private static final Pattern TAG_PATTERN = Pattern.compile("<\\s*/?\\s*([a-zA-Z0-9]+)");
    private static final int ARRAY_CHUNK_SIZE = 50_000;
    private static final int MATRIX_ROW_BLOCK = 16;

    private final Lab2Config config;
    private final Task1InputGenerator inputGenerator;

    public IndependentTaskBenchmarks(Lab2Config config) {
        this(config, new Task1InputGenerator(config));
    }

    IndependentTaskBenchmarks(Lab2Config config, Task1InputGenerator inputGenerator) {
        this.config = config;
        this.inputGenerator = inputGenerator;
    }

    public List<BenchmarkResult> run() throws Exception {
        Task1Input input = inputGenerator.prepare(config.workingDirectory().resolve("task1"));
        return runBenchmarks(input);
    }

    private List<BenchmarkResult> runBenchmarks(Task1Input input) throws Exception {
        List<BenchmarkResult> results = new ArrayList<>();
        results.add(benchmarkTagCountSequential(input.htmlFiles()));
        for (int threadCount : config.threadCounts()) {
            results.add(benchmarkTagCountMapReduce(input.htmlFiles(), threadCount));
            results.add(benchmarkTagCountForkJoin(input.htmlFiles(), threadCount));
            results.add(benchmarkTagCountWorkerPool(input.htmlFiles(), threadCount));
        }

        results.add(benchmarkArrayStatsSequential(input.numbers()));
        for (int threadCount : config.threadCounts()) {
            results.add(benchmarkArrayStatsMapReduce(input.numbers(), threadCount));
            results.add(benchmarkArrayStatsForkJoin(input.numbers(), threadCount));
            results.add(benchmarkArrayStatsWorkerPool(input.numbers(), threadCount));
        }

        results.add(benchmarkMatrixSequential(input.matrixA(), input.matrixB()));
        for (int threadCount : config.threadCounts()) {
            results.add(benchmarkMatrixMapReduce(input.matrixA(), input.matrixB(), threadCount));
            results.add(benchmarkMatrixForkJoin(input.matrixA(), input.matrixB(), threadCount));
            results.add(benchmarkMatrixWorkerPool(input.matrixA(), input.matrixB(), threadCount));
        }

        return results;
    }

    private BenchmarkResult benchmarkTagCountSequential(List<java.nio.file.Path> htmlFiles) throws IOException {
        long started = System.nanoTime();
        Map<String, Integer> counts = new HashMap<>();
        for (java.nio.file.Path htmlFile : htmlFiles) {
            mergeCounts(counts, countTags(htmlFile));
        }
        long elapsed = System.nanoTime() - started;
        return new BenchmarkResult("HTML tag frequency", "Sequential", 1, elapsed, summarizeTagCounts(counts));
    }

    private BenchmarkResult benchmarkTagCountMapReduce(List<java.nio.file.Path> htmlFiles, int threadCount) {
        ForkJoinPool pool = new ForkJoinPool(threadCount);
        long started = System.nanoTime();
        Map<String, Integer> counts = pool.submit(() -> htmlFiles.parallelStream()
                .map(this::countTagsUnchecked)
                .reduce(this::mergeIntoCopy)
                .orElseGet(HashMap::new)).join();
        long elapsed = System.nanoTime() - started;
        pool.shutdown();
        return new BenchmarkResult("HTML tag frequency", "Map-Reduce", threadCount, elapsed, summarizeTagCounts(counts));
    }

    private BenchmarkResult benchmarkTagCountForkJoin(List<java.nio.file.Path> htmlFiles, int threadCount) {
        ForkJoinPool pool = new ForkJoinPool(threadCount);
        long started = System.nanoTime();
        Map<String, Integer> counts = pool.invoke(new TagCountTask(htmlFiles, 0, htmlFiles.size()));
        long elapsed = System.nanoTime() - started;
        pool.shutdown();
        return new BenchmarkResult("HTML tag frequency", "Fork-Join", threadCount, elapsed, summarizeTagCounts(counts));
    }

    private BenchmarkResult benchmarkTagCountWorkerPool(List<java.nio.file.Path> htmlFiles, int threadCount) throws Exception {
        long started = System.nanoTime();
        Map<String, Integer> counts = new HashMap<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            List<Callable<Map<String, Integer>>> tasks = new ArrayList<>();
            int chunkSize = Math.max(1, (htmlFiles.size() + threadCount - 1) / threadCount);
            for (int start = 0; start < htmlFiles.size(); start += chunkSize) {
                int from = start;
                int to = Math.min(htmlFiles.size(), start + chunkSize);
                tasks.add(() -> {
                    Map<String, Integer> local = new HashMap<>();
                    for (int i = from; i < to; i++) {
                        mergeCounts(local, countTags(htmlFiles.get(i)));
                    }
                    return local;
                });
            }
            for (Future<Map<String, Integer>> future : executor.invokeAll(tasks)) {
                mergeCounts(counts, future.get());
            }
        }
        long elapsed = System.nanoTime() - started;
        return new BenchmarkResult("HTML tag frequency", "Worker Pool", threadCount, elapsed, summarizeTagCounts(counts));
    }

    private BenchmarkResult benchmarkArrayStatsSequential(long[] numbers) {
        long started = System.nanoTime();
        SortedStats stats = summarizeRange(numbers, 0, numbers.length);
        long elapsed = System.nanoTime() - started;
        return new BenchmarkResult("Array statistics", "Sequential", 1, elapsed, summarizeStats(stats));
    }

    private BenchmarkResult benchmarkArrayStatsMapReduce(long[] numbers, int threadCount) {
        ForkJoinPool pool = new ForkJoinPool(threadCount);
        long started = System.nanoTime();
        SortedStats stats = pool.submit(() -> IntStream.range(0, chunkCount(numbers.length))
                .parallel()
                .mapToObj(chunkIndex -> {
                    int start = chunkIndex * ARRAY_CHUNK_SIZE;
                    int end = Math.min(numbers.length, start + ARRAY_CHUNK_SIZE);
                    return summarizeRange(numbers, start, end);
                })
                .reduce(this::mergeStats)
                .orElseThrow()).join();
        long elapsed = System.nanoTime() - started;
        pool.shutdown();
        return new BenchmarkResult("Array statistics", "Map-Reduce", threadCount, elapsed, summarizeStats(stats));
    }

    private BenchmarkResult benchmarkArrayStatsForkJoin(long[] numbers, int threadCount) {
        ForkJoinPool pool = new ForkJoinPool(threadCount);
        long started = System.nanoTime();
        SortedStats stats = pool.invoke(new StatsTask(numbers, 0, numbers.length));
        long elapsed = System.nanoTime() - started;
        pool.shutdown();
        return new BenchmarkResult("Array statistics", "Fork-Join", threadCount, elapsed, summarizeStats(stats));
    }

    private BenchmarkResult benchmarkArrayStatsWorkerPool(long[] numbers, int threadCount) throws Exception {
        long started = System.nanoTime();
        SortedStats merged;
        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            List<Callable<SortedStats>> tasks = new ArrayList<>();
            for (int start = 0; start < numbers.length; start += ARRAY_CHUNK_SIZE) {
                int from = start;
                int to = Math.min(numbers.length, start + ARRAY_CHUNK_SIZE);
                tasks.add(() -> summarizeRange(numbers, from, to));
            }
            List<Future<SortedStats>> futures = executor.invokeAll(tasks);
            merged = null;
            for (Future<SortedStats> future : futures) {
                merged = merged == null ? future.get() : mergeStats(merged, future.get());
            }
        }
        long elapsed = System.nanoTime() - started;
        return new BenchmarkResult("Array statistics", "Worker Pool", threadCount, elapsed, summarizeStats(merged));
    }

    private BenchmarkResult benchmarkMatrixSequential(double[][] matrixA, double[][] matrixB) {
        double[][] result = new double[matrixA.length][matrixB[0].length];
        long started = System.nanoTime();
        multiplyRows(matrixA, matrixB, result, 0, matrixA.length);
        long elapsed = System.nanoTime() - started;
        return new BenchmarkResult("Matrix multiplication", "Sequential", 1, elapsed, summarizeMatrix(result));
    }

    private BenchmarkResult benchmarkMatrixMapReduce(double[][] matrixA, double[][] matrixB, int threadCount) {
        int size = matrixA.length;
        int blockSize = Math.max(MATRIX_ROW_BLOCK, (size + threadCount - 1) / threadCount);
        ForkJoinPool pool = new ForkJoinPool(threadCount);
        long started = System.nanoTime();
        List<RowBlockResult> rowBlocks = pool.submit(() -> IntStream.range(0, (size + blockSize - 1) / blockSize)
                .parallel()
                .mapToObj(blockIndex -> {
                    int start = blockIndex * blockSize;
                    int end = Math.min(size, start + blockSize);
                    return new RowBlockResult(start, multiplyRowBlock(matrixA, matrixB, start, end));
                })
                .sorted((left, right) -> Integer.compare(left.startRow(), right.startRow()))
                .toList()).join();
        double[][] result = new double[size][matrixB[0].length];
        for (RowBlockResult rowBlock : rowBlocks) {
            for (int index = 0; index < rowBlock.rows().length; index++) {
                result[rowBlock.startRow() + index] = rowBlock.rows()[index];
            }
        }
        long elapsed = System.nanoTime() - started;
        pool.shutdown();
        return new BenchmarkResult("Matrix multiplication", "Map-Reduce", threadCount, elapsed, summarizeMatrix(result));
    }

    private BenchmarkResult benchmarkMatrixForkJoin(double[][] matrixA, double[][] matrixB, int threadCount) {
        double[][] result = new double[matrixA.length][matrixB[0].length];
        ForkJoinPool pool = new ForkJoinPool(threadCount);
        long started = System.nanoTime();
        pool.invoke(new MatrixMultiplyAction(matrixA, matrixB, result, 0, matrixA.length));
        long elapsed = System.nanoTime() - started;
        pool.shutdown();
        return new BenchmarkResult("Matrix multiplication", "Fork-Join", threadCount, elapsed, summarizeMatrix(result));
    }

    private BenchmarkResult benchmarkMatrixWorkerPool(double[][] matrixA, double[][] matrixB, int threadCount) throws Exception {
        double[][] result = new double[matrixA.length][matrixB[0].length];
        long started = System.nanoTime();
        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            List<Callable<Void>> tasks = new ArrayList<>();
            int chunkSize = Math.max(MATRIX_ROW_BLOCK, (matrixA.length + threadCount - 1) / threadCount);
            for (int start = 0; start < matrixA.length; start += chunkSize) {
                int from = start;
                int to = Math.min(matrixA.length, start + chunkSize);
                tasks.add(() -> {
                    multiplyRows(matrixA, matrixB, result, from, to);
                    return null;
                });
            }
            for (Future<Void> future : executor.invokeAll(tasks)) {
                future.get();
            }
        }
        long elapsed = System.nanoTime() - started;
        return new BenchmarkResult("Matrix multiplication", "Worker Pool", threadCount, elapsed, summarizeMatrix(result));
    }

    private Map<String, Integer> countTags(java.nio.file.Path htmlFile) throws IOException {
        return countTags(Files.readString(htmlFile, StandardCharsets.UTF_8));
    }

    private Map<String, Integer> countTagsUnchecked(java.nio.file.Path htmlFile) {
        try {
            return countTags(htmlFile);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read " + htmlFile, exception);
        }
    }

    private Map<String, Integer> countTags(String html) {
        Map<String, Integer> counts = new HashMap<>();
        Matcher matcher = TAG_PATTERN.matcher(html);
        while (matcher.find()) {
            counts.merge(matcher.group(1).toLowerCase(), 1, Integer::sum);
        }
        return counts;
    }

    private Map<String, Integer> mergeCounts(Map<String, Integer> left, Map<String, Integer> right) {
        for (Map.Entry<String, Integer> entry : right.entrySet()) {
            left.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }
        return left;
    }

    private Map<String, Integer> mergeIntoCopy(Map<String, Integer> left, Map<String, Integer> right) {
        Map<String, Integer> merged = new HashMap<>(left);
        return mergeCounts(merged, right);
    }

    private String summarizeTagCounts(Map<String, Integer> counts) {
        Map<String, Integer> sorted = new LinkedHashMap<>();
        counts.entrySet().stream()
                .sorted((left, right) -> Integer.compare(right.getValue(), left.getValue()))
                .limit(5)
                .forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
        return "topTags=" + sorted;
    }

    private SortedStats summarizeRange(long[] numbers, int startInclusive, int endExclusive) {
        long[] copy = Arrays.copyOfRange(numbers, startInclusive, endExclusive);
        Arrays.sort(copy);
        long sum = 0;
        for (long number : copy) {
            sum += number;
        }
        return new SortedStats(copy[0], copy[copy.length - 1], sum, copy);
    }

    private SortedStats mergeStats(SortedStats left, SortedStats right) {
        long[] merged = new long[left.sorted().length + right.sorted().length];
        int leftIndex = 0;
        int rightIndex = 0;
        int outIndex = 0;
        while (leftIndex < left.sorted().length && rightIndex < right.sorted().length) {
            if (left.sorted()[leftIndex] <= right.sorted()[rightIndex]) {
                merged[outIndex++] = left.sorted()[leftIndex++];
            } else {
                merged[outIndex++] = right.sorted()[rightIndex++];
            }
        }
        while (leftIndex < left.sorted().length) {
            merged[outIndex++] = left.sorted()[leftIndex++];
        }
        while (rightIndex < right.sorted().length) {
            merged[outIndex++] = right.sorted()[rightIndex++];
        }
        return new SortedStats(
                Math.min(left.min(), right.min()),
                Math.max(left.max(), right.max()),
                left.sum() + right.sum(),
                merged
        );
    }

    private String summarizeStats(SortedStats stats) {
        double median;
        int middle = stats.sorted().length / 2;
        if (stats.sorted().length % 2 == 0) {
            median = (stats.sorted()[middle - 1] + stats.sorted()[middle]) / 2.0;
        } else {
            median = stats.sorted()[middle];
        }
        double average = (double) stats.sum() / stats.sorted().length;
        return String.format("min=%d max=%d median=%.2f avg=%.2f", stats.min(), stats.max(), median, average);
    }

    private void multiplyRows(double[][] matrixA, double[][] matrixB, double[][] result, int startRow, int endRow) {
        int columns = matrixB[0].length;
        int commonSize = matrixB.length;
        for (int row = startRow; row < endRow; row++) {
            double[] resultRow = result[row];
            double[] sourceRow = matrixA[row];
            for (int pivot = 0; pivot < commonSize; pivot++) {
                double left = sourceRow[pivot];
                double[] rightRow = matrixB[pivot];
                for (int column = 0; column < columns; column++) {
                    resultRow[column] += left * rightRow[column];
                }
            }
        }
    }

    private double[][] multiplyRowBlock(double[][] matrixA, double[][] matrixB, int startRow, int endRow) {
        double[][] block = new double[endRow - startRow][matrixB[0].length];
        int columns = matrixB[0].length;
        int commonSize = matrixB.length;
        for (int row = startRow; row < endRow; row++) {
            double[] sourceRow = matrixA[row];
            double[] resultRow = block[row - startRow];
            for (int pivot = 0; pivot < commonSize; pivot++) {
                double left = sourceRow[pivot];
                double[] rightRow = matrixB[pivot];
                for (int column = 0; column < columns; column++) {
                    resultRow[column] += left * rightRow[column];
                }
            }
        }
        return block;
    }

    private String summarizeMatrix(double[][] matrix) {
        double checksum = 0.0;
        int step = Math.max(1, matrix.length / 8);
        for (int row = 0; row < matrix.length; row += step) {
            for (int column = 0; column < matrix[row].length; column += step) {
                checksum += matrix[row][column] * (row + 1) / (column + 1.0);
            }
        }
        return String.format("checksum=%.5f", checksum);
    }

    private int chunkCount(int length) {
        return (length + ARRAY_CHUNK_SIZE - 1) / ARRAY_CHUNK_SIZE;
    }

    private record SortedStats(long min, long max, long sum, long[] sorted) {
    }

    private record RowBlockResult(int startRow, double[][] rows) {
    }

    private final class TagCountTask extends RecursiveTask<Map<String, Integer>> {
        private final List<java.nio.file.Path> files;
        private final int startInclusive;
        private final int endExclusive;

        private TagCountTask(List<java.nio.file.Path> files, int startInclusive, int endExclusive) {
            this.files = files;
            this.startInclusive = startInclusive;
            this.endExclusive = endExclusive;
        }

        @Override
        protected Map<String, Integer> compute() {
            if (endExclusive - startInclusive <= 32) {
                Map<String, Integer> local = new HashMap<>();
                for (int i = startInclusive; i < endExclusive; i++) {
                    mergeCounts(local, countTagsUnchecked(files.get(i)));
                }
                return local;
            }
            int middle = (startInclusive + endExclusive) / 2;
            TagCountTask left = new TagCountTask(files, startInclusive, middle);
            TagCountTask right = new TagCountTask(files, middle, endExclusive);
            left.fork();
            Map<String, Integer> rightResult = right.compute();
            Map<String, Integer> leftResult = left.join();
            return mergeCounts(leftResult, rightResult);
        }
    }

    private final class StatsTask extends RecursiveTask<SortedStats> {
        private final long[] numbers;
        private final int startInclusive;
        private final int endExclusive;

        private StatsTask(long[] numbers, int startInclusive, int endExclusive) {
            this.numbers = numbers;
            this.startInclusive = startInclusive;
            this.endExclusive = endExclusive;
        }

        @Override
        protected SortedStats compute() {
            if (endExclusive - startInclusive <= ARRAY_CHUNK_SIZE) {
                return summarizeRange(numbers, startInclusive, endExclusive);
            }
            int middle = (startInclusive + endExclusive) / 2;
            StatsTask left = new StatsTask(numbers, startInclusive, middle);
            StatsTask right = new StatsTask(numbers, middle, endExclusive);
            left.fork();
            SortedStats rightStats = right.compute();
            SortedStats leftStats = left.join();
            return mergeStats(leftStats, rightStats);
        }
    }

    private final class MatrixMultiplyAction extends RecursiveAction {
        private final double[][] matrixA;
        private final double[][] matrixB;
        private final double[][] result;
        private final int startRow;
        private final int endRow;

        private MatrixMultiplyAction(double[][] matrixA, double[][] matrixB, double[][] result, int startRow, int endRow) {
            this.matrixA = matrixA;
            this.matrixB = matrixB;
            this.result = result;
            this.startRow = startRow;
            this.endRow = endRow;
        }

        @Override
        protected void compute() {
            if (endRow - startRow <= MATRIX_ROW_BLOCK) {
                multiplyRows(matrixA, matrixB, result, startRow, endRow);
                return;
            }
            int middle = (startRow + endRow) / 2;
            invokeAll(
                    new MatrixMultiplyAction(matrixA, matrixB, result, startRow, middle),
                    new MatrixMultiplyAction(matrixA, matrixB, result, middle, endRow)
            );
        }
    }
}
