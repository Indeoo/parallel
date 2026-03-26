package org.venherak.lab3.task1;

import org.venherak.lab3.BenchmarkResult;
import org.venherak.lab3.Lab3Config;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public final class BankTransferBenchmarks {
    private final Lab3Config config;

    public BankTransferBenchmarks(Lab3Config config) {
        this.config = config;
    }

    public List<BenchmarkResult> run() throws InterruptedException {
        List<BenchmarkResult> results = new ArrayList<>();
        for (int threadCount : config.threadCounts()) {
            results.add(runRaceScenario(threadCount, false));
            results.add(runRaceScenario(threadCount, true));
        }
        results.add(runDeadlockScenario());
        return results;
    }

    private BenchmarkResult runRaceScenario(int threadCount, boolean safe) throws InterruptedException {
        Bank bank = Bank.create(config.accountCount(), 10_000, new SplittableRandom(7L));
        long initialTotal = bank.totalBalance();
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        List<Thread> threads = new ArrayList<>(threadCount);
        long startedAt = System.nanoTime();

        for (int index = 0; index < threadCount; index++) {
            int workerId = index;
            Thread thread = new Thread(() -> {
                SplittableRandom random = new SplittableRandom(1000L + workerId);
                ready.countDown();
                await(start);
                for (int operation = 0; operation < config.operationsPerThread(); operation++) {
                    int from = random.nextInt(bank.size());
                    int to = random.nextInt(bank.size() - 1);
                    if (to >= from) {
                        to++;
                    }
                    int amount = random.nextInt(1, 250);
                    if (safe) {
                        bank.transferOrdered(from, to, amount);
                    } else {
                        bank.transferUnsafe(from, to, amount);
                    }
                }
                done.countDown();
            }, (safe ? "safe-" : "unsafe-") + workerId);
            threads.add(thread);
            thread.start();
        }

        ready.await();
        startedAt = System.nanoTime();
        start.countDown();
        done.await();
        long elapsed = System.nanoTime() - startedAt;
        long finalTotal = bank.totalBalance();
        long drift = finalTotal - initialTotal;
        int negativeBalances = bank.negativeBalances();

        return new BenchmarkResult(
                "bank-transfer",
                safe ? "safe-ordered-locking" : "unsafe-race",
                threadCount,
                elapsed,
                "initial=" + initialTotal
                        + ", final=" + finalTotal
                        + ", drift=" + drift
                        + ", negativeAccounts=" + negativeBalances
        );
    }

    private BenchmarkResult runDeadlockScenario() throws InterruptedException {
        Bank bank = Bank.create(2, 1_000, new SplittableRandom(91L));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Thread first = new Thread(() -> {
            ready.countDown();
            await(start);
            bank.transferDeadlockProne(0, 1, 100);
        }, "deadlock-a");
        Thread second = new Thread(() -> {
            ready.countDown();
            await(start);
            bank.transferDeadlockProne(1, 0, 100);
        }, "deadlock-b");

        first.setDaemon(true);
        second.setDaemon(true);
        first.start();
        second.start();

        ready.await();
        long startedAt = System.nanoTime();
        start.countDown();
        first.join(TimeUnit.MILLISECONDS.toMillis(400));
        second.join(TimeUnit.MILLISECONDS.toMillis(400));
        long elapsed = System.nanoTime() - startedAt;
        boolean deadlockDetected = first.isAlive() && second.isAlive();

        return new BenchmarkResult(
                "bank-transfer",
                "deadlock-prone",
                2,
                elapsed,
                "deadlockDetected=" + deadlockDetected
                        + ", reason=locks acquired in opposite order"
        );
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for benchmark start", exception);
        }
    }

    private static final class Bank {
        private final Account[] accounts;

        private Bank(Account[] accounts) {
            this.accounts = accounts;
        }

        static Bank create(int accountCount, int maxBalance, SplittableRandom random) {
            Account[] accounts = new Account[accountCount];
            int minBalance = Math.max(1, Math.min(2_000, maxBalance));
            int upperExclusive = Math.max(minBalance + 1, maxBalance + 1);
            for (int index = 0; index < accountCount; index++) {
                accounts[index] = new Account(index, random.nextInt(minBalance, upperExclusive));
            }
            return new Bank(accounts);
        }

        int size() {
            return accounts.length;
        }

        void transferUnsafe(int fromIndex, int toIndex, int amount) {
            Account from = accounts[fromIndex];
            Account to = accounts[toIndex];
            if (from.balance < amount) {
                return;
            }
            from.balance -= amount;
            Thread.yield();
            to.balance += amount;
        }

        void transferOrdered(int fromIndex, int toIndex, int amount) {
            Account from = accounts[fromIndex];
            Account to = accounts[toIndex];
            Account first = from.id < to.id ? from : to;
            Account second = from.id < to.id ? to : from;
            first.lock.lock();
            second.lock.lock();
            try {
                if (from.balance >= amount) {
                    from.balance -= amount;
                    to.balance += amount;
                }
            } finally {
                second.lock.unlock();
                first.lock.unlock();
            }
        }

        void transferDeadlockProne(int fromIndex, int toIndex, int amount) {
            Account from = accounts[fromIndex];
            Account to = accounts[toIndex];
            from.lock.lock();
            try {
                sleepQuietly(50);
                to.lock.lock();
                try {
                    if (from.balance >= amount) {
                        from.balance -= amount;
                        to.balance += amount;
                    }
                } finally {
                    to.lock.unlock();
                }
            } finally {
                from.lock.unlock();
            }
        }

        long totalBalance() {
            long total = 0;
            for (Account account : accounts) {
                total += account.balance;
            }
            return total;
        }

        int negativeBalances() {
            int count = 0;
            for (Account account : accounts) {
                if (account.balance < 0) {
                    count++;
                }
            }
            return count;
        }

        private static void sleepQuietly(long millis) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static final class Account {
        private final int id;
        private final ReentrantLock lock = new ReentrantLock();
        private int balance;

        private Account(int id, int balance) {
            this.id = id;
            this.balance = balance;
        }
    }
}
