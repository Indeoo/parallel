package org.venherak.lab2.task2.generator;

import org.venherak.lab2.Lab2Config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.SplittableRandom;

public final class TransactionsFileGenerator {
    private static final String[] PRODUCT_TYPES = {"book", "course", "hardware", "software", "ticket", "subscription"};

    private final Lab2Config config;

    public TransactionsFileGenerator(Lab2Config config) {
        this.config = config;
    }

    public Path prepare(Path root) throws IOException {
        Files.createDirectories(root);
        Path file = root.resolve("transactions.csv");
        SplittableRandom random = new SplittableRandom(2026_2L);
        try (var writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write("userId,amount,currency,date,type");
            writer.newLine();
            for (int index = 0; index < config.transactionCount(); index++) {
                long userId = 10_000L + random.nextLong(50_000L);
                long amount = 100 + random.nextLong(150_000L);
                Currency currency = Currency.values()[random.nextInt(Currency.values().length)];
                LocalDate date = LocalDate.of(2026, 1, 1).plusDays(random.nextInt(365));
                String type = PRODUCT_TYPES[random.nextInt(PRODUCT_TYPES.length)];
                writer.write(userId + "," + amount + "," + currency + "," + date + "," + type);
                writer.newLine();
            }
        }
        return file;
    }

    private enum Currency {
        USD, EUR, UAH, GBP
    }
}
