package org.venherak.lab2.task1.generator;

import org.venherak.lab2.Lab2Config;
import org.venherak.lab2.task1.Task1Input;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

public final class Task1InputGenerator {
    private static final String[] TAG_NAMES = {
            "html", "body", "div", "span", "main", "section", "article",
            "p", "a", "ul", "li", "table", "tr", "td", "header", "footer", "img"
    };
    private static final String[] SENTENCES = {
            "parallel patterns improve throughput",
            "fork join balances recursive workloads",
            "worker pools reduce thread creation overhead",
            "map reduce scales data aggregation",
            "java executors simplify concurrency management"
    };

    private final Lab2Config config;

    public Task1InputGenerator(Lab2Config config) {
        this.config = config;
    }

    public Task1Input prepare(Path root) throws IOException {
        Files.createDirectories(root);
        return new Task1Input(
                prepareHtmlDocuments(root.resolve("html")),
                generateNumbers(),
                generateMatrix(17),
                generateMatrix(31)
        );
    }

    private List<Path> prepareHtmlDocuments(Path directory) throws IOException {
        if (Files.exists(directory)) {
            clearDirectory(directory);
        }
        Files.createDirectories(directory);

        SplittableRandom random = new SplittableRandom(20260319L);
        List<Path> files = new ArrayList<>();
        for (int index = 0; index < config.htmlDocumentCount(); index++) {
            Path path = directory.resolve("doc-" + index + ".html");
            Files.writeString(path, buildHtmlDocument(random, index), StandardCharsets.UTF_8);
            files.add(path);
        }
        return files;
    }

    private String buildHtmlDocument(SplittableRandom random, int index) {
        int elements = 40 + random.nextInt(140);
        StringBuilder builder = new StringBuilder(elements * 64);
        builder.append("<html><body><main data-id=\"").append(index).append("\">");
        for (int i = 0; i < elements; i++) {
            String tag = TAG_NAMES[random.nextInt(TAG_NAMES.length)];
            builder.append('<').append(tag).append(" class=\"c").append(i % 7).append("\">");
            builder.append(SENTENCES[random.nextInt(SENTENCES.length)]);
            if (random.nextInt(4) == 0) {
                builder.append("<span>nested text ").append(i).append("</span>");
            }
            builder.append("</").append(tag).append('>');
        }
        builder.append("</main></body></html>");
        return builder.toString();
    }

    private long[] generateNumbers() {
        SplittableRandom random = new SplittableRandom(702_2026L);
        long[] numbers = new long[config.numberCount()];
        for (int i = 0; i < numbers.length; i++) {
            long value = random.nextLong(2_000_000_000L) - 1_000_000_000L;
            numbers[i] = value + (i % 17 == 0 ? i : -i);
        }
        return numbers;
    }

    private double[][] generateMatrix(int seedOffset) {
        int size = config.matrixSize();
        SplittableRandom random = new SplittableRandom(99_000L + seedOffset);
        double[][] matrix = new double[size][size];
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                matrix[row][column] = random.nextDouble(-5.0, 5.0);
            }
        }
        return matrix;
    }

    private void clearDirectory(Path directory) throws IOException {
        try (var stream = Files.walk(directory)) {
            stream.sorted((left, right) -> right.compareTo(left)).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    throw new IllegalStateException("Cannot delete " + path, exception);
                }
            });
        }
    }
}
