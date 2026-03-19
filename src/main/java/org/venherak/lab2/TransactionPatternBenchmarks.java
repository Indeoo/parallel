package org.venherak.lab2;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.LongAdder;

final class TransactionPatternBenchmarks {
    private static final String LINE_POISON = "__END__";
    private static final Transaction TRANSACTION_POISON = new Transaction(-1L, 0L, Currency.USD, LocalDate.MIN, "POISON");
    private static final MoneyAmount MONEY_POISON = new MoneyAmount(Long.MIN_VALUE, Long.MIN_VALUE);
    private static final String[] PRODUCT_TYPES = {"book", "course", "hardware", "software", "ticket", "subscription"};

    private final Lab2Config config;
    private final Map<Currency, BigDecimal> rates = Map.of(
            Currency.USD, BigDecimal.ONE,
            Currency.EUR, new BigDecimal("1.09"),
            Currency.UAH, new BigDecimal("0.026"),
            Currency.GBP, new BigDecimal("1.27")
    );

    TransactionPatternBenchmarks(Lab2Config config) {
        this.config = config;
    }

    List<BenchmarkResult> run() throws Exception {
        Path root = config.workingDirectory().resolve("task2");
        Files.createDirectories(root);
        Path transactionsFile = generateTransactions(root.resolve("transactions.csv"));

        List<BenchmarkResult> results = new ArrayList<>();
        results.add(benchmarkSequential(transactionsFile));
        for (int threadCount : config.threadCounts()) {
            results.add(benchmarkProducerConsumer(transactionsFile, threadCount));
            results.add(benchmarkPipeline(transactionsFile, threadCount));
        }
        return results;
    }

    private Path generateTransactions(Path file) throws IOException {
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

    private BenchmarkResult benchmarkSequential(Path file) throws IOException {
        long started = System.nanoTime();
        MoneyAmount result = processSequential(file);
        long elapsed = System.nanoTime() - started;
        return new BenchmarkResult("Financial transactions", "Sequential", 1, elapsed, summarize(result));
    }

    private BenchmarkResult benchmarkProducerConsumer(Path file, int threadCount) throws Exception {
        BlockingQueue<String> queue = new ArrayBlockingQueue<>(4_096);
        LongAdder totalCents = new LongAdder();
        LongAdder cashbackCents = new LongAdder();

        long started = System.nanoTime();
        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount + 1)) {
            Future<?> producer = executor.submit(() -> produceLines(file, queue, threadCount));
            List<Callable<Void>> consumers = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                consumers.add(() -> {
                    consumeTransactions(queue, totalCents, cashbackCents);
                    return null;
                });
            }
            for (Future<Void> future : executor.invokeAll(consumers)) {
                future.get();
            }
            producer.get();
        }
        long elapsed = System.nanoTime() - started;
        return new BenchmarkResult(
                "Financial transactions",
                "Producer-Consumer",
                threadCount,
                elapsed,
                summarize(new MoneyAmount(totalCents.sum(), cashbackCents.sum()))
        );
    }

    private BenchmarkResult benchmarkPipeline(Path file, int threadCount) throws Exception {
        BlockingQueue<String> lineQueue = new ArrayBlockingQueue<>(4_096);
        BlockingQueue<Transaction> parsedQueue = new ArrayBlockingQueue<>(4_096);
        BlockingQueue<Transaction> convertedQueue = new ArrayBlockingQueue<>(4_096);
        BlockingQueue<MoneyAmount> resultQueue = new ArrayBlockingQueue<>(4_096);

        int stageWorkers = Math.max(1, threadCount);

        long started = System.nanoTime();
        try (ExecutorService executor = Executors.newFixedThreadPool(stageWorkers * 3 + 2)) {
            Future<?> producer = executor.submit(() -> produceLines(file, lineQueue, stageWorkers));
            List<Future<Void>> parsers = new ArrayList<>();
            for (int i = 0; i < stageWorkers; i++) {
                parsers.add(executor.submit(() -> {
                    parseStage(lineQueue, parsedQueue);
                    return null;
                }));
            }

            List<Future<Void>> converters = new ArrayList<>();
            for (int i = 0; i < stageWorkers; i++) {
                converters.add(executor.submit(() -> {
                    convertStage(parsedQueue, convertedQueue);
                    return null;
                }));
            }

            List<Future<Void>> cashbackWorkers = new ArrayList<>();
            for (int i = 0; i < stageWorkers; i++) {
                cashbackWorkers.add(executor.submit(() -> {
                    cashbackStage(convertedQueue, resultQueue);
                    return null;
                }));
            }

            Future<MoneyAmount> aggregator = executor.submit(() -> aggregateStage(resultQueue, stageWorkers));

            producer.get();
            for (Future<Void> future : parsers) {
                future.get();
            }
            for (Future<Void> future : converters) {
                future.get();
            }
            for (Future<Void> future : cashbackWorkers) {
                future.get();
            }

            MoneyAmount result = aggregator.get();
            long elapsed = System.nanoTime() - started;
            return new BenchmarkResult("Financial transactions", "Pipeline", threadCount, elapsed, summarize(result));
        }
    }

    private MoneyAmount processSequential(Path file) throws IOException {
        long total = 0;
        long cashback = 0;
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            reader.readLine();
            String line;
            while ((line = reader.readLine()) != null) {
                MoneyAmount amount = processTransaction(parse(line));
                total += amount.totalCents();
                cashback += amount.cashbackCents();
            }
        }
        return new MoneyAmount(total, cashback);
    }

    private void produceLines(Path file, BlockingQueue<String> queue, int poisonCount) {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            reader.readLine();
            String line;
            while ((line = reader.readLine()) != null) {
                queue.put(line);
            }
            for (int i = 0; i < poisonCount; i++) {
                queue.put(LINE_POISON);
            }
        } catch (IOException | InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Cannot produce lines from " + file, exception);
        }
    }

    private void consumeTransactions(BlockingQueue<String> queue, LongAdder totalCents, LongAdder cashbackCents) {
        try {
            while (true) {
                String line = queue.take();
                if (LINE_POISON.equals(line)) {
                    return;
                }
                MoneyAmount amount = processTransaction(parse(line));
                totalCents.add(amount.totalCents());
                cashbackCents.add(amount.cashbackCents());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Consumer interrupted", exception);
        }
    }

    private void parseStage(BlockingQueue<String> lineQueue, BlockingQueue<Transaction> parsedQueue) {
        try {
            while (true) {
                String line = lineQueue.take();
                if (LINE_POISON.equals(line)) {
                    parsedQueue.put(TRANSACTION_POISON);
                    return;
                }
                parsedQueue.put(parse(line));
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Parser interrupted", exception);
        }
    }

    private void convertStage(BlockingQueue<Transaction> parsedQueue, BlockingQueue<Transaction> convertedQueue) {
        try {
            while (true) {
                Transaction transaction = parsedQueue.take();
                if (transaction == TRANSACTION_POISON) {
                    convertedQueue.put(TRANSACTION_POISON);
                    return;
                }
                convertedQueue.put(convertCurrency(transaction));
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Converter interrupted", exception);
        }
    }

    private void cashbackStage(BlockingQueue<Transaction> convertedQueue, BlockingQueue<MoneyAmount> resultQueue) {
        try {
            while (true) {
                Transaction transaction = convertedQueue.take();
                if (transaction == TRANSACTION_POISON) {
                    resultQueue.put(MONEY_POISON);
                    return;
                }
                resultQueue.put(processConvertedTransaction(transaction));
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Cashback stage interrupted", exception);
        }
    }

    private MoneyAmount aggregateStage(BlockingQueue<MoneyAmount> resultQueue, int poisonCount) {
        long total = 0;
        long cashback = 0;
        int seenPoisons = 0;
        try {
            while (seenPoisons < poisonCount) {
                MoneyAmount amount = resultQueue.take();
                if (amount == MONEY_POISON) {
                    seenPoisons++;
                    continue;
                }
                total += amount.totalCents();
                cashback += amount.cashbackCents();
            }
            return new MoneyAmount(total, cashback);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Aggregator interrupted", exception);
        }
    }

    private Transaction parse(String line) {
        String[] parts = line.split(",", 5);
        return new Transaction(
                Long.parseLong(parts[0]),
                Long.parseLong(parts[1]),
                Currency.valueOf(parts[2]),
                LocalDate.parse(parts[3]),
                parts[4]
        );
    }

    private Transaction convertCurrency(Transaction transaction) {
        BigDecimal rate = rates.get(transaction.currency());
        long usdAmount = BigDecimal.valueOf(transaction.amountCents())
                .multiply(rate)
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
        return new Transaction(transaction.userId(), usdAmount, Currency.USD, transaction.date(), transaction.type());
    }

    private MoneyAmount processTransaction(Transaction transaction) {
        return processConvertedTransaction(convertCurrency(transaction));
    }

    private MoneyAmount processConvertedTransaction(Transaction transaction) {
        long cashback = eligibleForCashback(transaction.userId()) ? transaction.amountCents() / 5 : 0;
        long total = transaction.amountCents() - cashback;
        return new MoneyAmount(total, cashback);
    }

    private boolean eligibleForCashback(long userId) {
        return userId % 10 == 0 || userId % 17 == 0;
    }

    private String summarize(MoneyAmount amount) {
        return "finalUsdCents=" + amount.totalCents() + " cashbackUsdCents=" + amount.cashbackCents();
    }

    private enum Currency {
        USD, EUR, UAH, GBP
    }

    private record Transaction(long userId, long amountCents, Currency currency, LocalDate date, String type) {
    }

    private record MoneyAmount(long totalCents, long cashbackCents) {
    }
}
