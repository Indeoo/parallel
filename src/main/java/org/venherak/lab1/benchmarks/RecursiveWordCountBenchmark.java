package org.venherak.lab1.benchmarks;

import org.venherak.lab1.BenchmarkArguments;
import org.venherak.lab1.BenchmarkConfig;
import org.venherak.lab1.BenchmarkRunner.BenchmarkResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

public final class RecursiveWordCountBenchmark implements BenchmarkTask {
    private static final String[] DICTIONARY = {
            "parallel", "thread", "executor", "future", "memory", "cache", "random", "matrix",
            "directory", "java", "benchmark", "worker", "result", "input", "output", "speedup"
    };

    private final Path rootDirectory;
    private final int textFileCount;
    private final int wordsPerFile;

    public RecursiveWordCountBenchmark(String[] args, Path rootDirectory) {
        this.rootDirectory = rootDirectory;
        this.textFileCount = BenchmarkArguments.intOption(args, "textFiles", 600);
        this.wordsPerFile = BenchmarkArguments.intOption(args, "wordsPerFile", 700);
    }

    @Override
    public String group() {
        return "I/O-bound";
    }

    @Override
    public String name() {
        return "Recursive word count";
    }

    @Override
    public List<String> configurationLines() {
        return List.of("textFiles=" + textFileCount, "wordsPerFile=" + wordsPerFile);
    }

    @Override
    public List<BenchmarkResult> run(BenchmarkConfig config) throws Exception {
        prepareDirectory();
        List<Path> files = collectTextFiles();

        List<BenchmarkResult> results = new ArrayList<>();
        results.add(benchmarkSequential(files));
        for (int threadCount : config.threadCounts()) {
            results.add(benchmarkParallel(files, threadCount));
        }
        return results;
    }

    private BenchmarkResult benchmarkSequential(List<Path> files) throws IOException {
        long started = System.nanoTime();
        long wordCount = 0;
        for (Path file : files) {
            wordCount += countWords(file);
        }
        long elapsed = System.nanoTime() - started;
        return new BenchmarkResult("Recursive word count", false, 1, elapsed, "words=" + wordCount);
    }

    private BenchmarkResult benchmarkParallel(List<Path> files, int threadCount) throws Exception {
        long started = System.nanoTime();
        long wordCount = 0;

        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            List<Callable<Long>> tasks = new ArrayList<>();
            for (Path file : files) {
                tasks.add(() -> countWords(file));
            }

            for (Future<Long> future : executor.invokeAll(tasks)) {
                wordCount += future.get();
            }
        }

        long elapsed = System.nanoTime() - started;
        return new BenchmarkResult("Recursive word count", true, threadCount, elapsed, "words=" + wordCount);
    }

    private void prepareDirectory() throws IOException {
        if (Files.exists(rootDirectory)) {
            deleteRecursively(rootDirectory);
        }
        Files.createDirectories(rootDirectory);
        generateFiles();
    }

    private void generateFiles() throws IOException {
        SplittableRandom random = new SplittableRandom(2026);
        for (int index = 0; index < textFileCount; index++) {
            Path nestedDirectory = rootDirectory
                    .resolve("batch-" + (index % 12))
                    .resolve("group-" + (index % 25));
            Files.createDirectories(nestedDirectory);

            Path file = nestedDirectory.resolve("file-" + index + ".txt");
            Files.writeString(file, buildText(random, wordsPerFile), StandardCharsets.UTF_8);
        }
    }

    private List<Path> collectTextFiles() throws IOException {
        try (Stream<Path> stream = Files.walk(rootDirectory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".txt"))
                    .sorted()
                    .toList();
        }
    }

    private long countWords(Path file) throws IOException {
        String content = Files.readString(file, StandardCharsets.UTF_8).trim();
        if (content.isEmpty()) {
            return 0;
        }
        return content.split("\\s+").length;
    }

    private String buildText(SplittableRandom random, int wordCount) {
        StringBuilder builder = new StringBuilder(wordCount * 8);
        for (int index = 0; index < wordCount; index++) {
            if (index > 0) {
                builder.append(index % 14 == 0 ? '\n' : ' ');
            }
            builder.append(DICTIONARY[random.nextInt(DICTIONARY.length)]);
        }
        return builder.toString();
    }

    private void deleteRecursively(Path directory) throws IOException {
        try (Stream<Path> stream = Files.walk(directory)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    throw new IllegalStateException("Cannot delete " + path, exception);
                }
            });
        }
    }
}
