package org.venherak.pr1;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public final class RaceConditionDemo {
    public RaceConditionResult run(int threadCount, int iterationsPerThread) throws InterruptedException {
        int unsafeValue = runUnsafe(threadCount, iterationsPerThread);
        int safeValue = runSafe(threadCount, iterationsPerThread);
        int incrementingThreads = (threadCount + 1) / 2;
        int decrementingThreads = threadCount / 2;
        int expectedValue = (incrementingThreads - decrementingThreads) * iterationsPerThread;
        return new RaceConditionResult(threadCount, iterationsPerThread, expectedValue, unsafeValue, safeValue);
    }

    private int runUnsafe(int threadCount, int iterationsPerThread) throws InterruptedException {
        MutableCounter counter = new MutableCounter();
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int index = 0; index < threadCount; index++) {
            final boolean increment = index % 2 == 0;
            Thread thread = new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return;
                }
                for (int iteration = 0; iteration < iterationsPerThread; iteration++) {
                    counter.addUnsafe(increment ? 1 : -1);
                }
            }, "race-unsafe-" + index);
            threads.add(thread);
            thread.start();
        }

        start.countDown();
        for (Thread thread : threads) {
            thread.join();
        }
        return counter.value();
    }

    private int runSafe(int threadCount, int iterationsPerThread) throws InterruptedException {
        MutableCounter counter = new MutableCounter();
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int index = 0; index < threadCount; index++) {
            final boolean increment = index % 2 == 0;
            Thread thread = new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return;
                }
                for (int iteration = 0; iteration < iterationsPerThread; iteration++) {
                    counter.addSafe(increment ? 1 : -1);
                }
            }, "race-safe-" + index);
            threads.add(thread);
            thread.start();
        }

        start.countDown();
        for (Thread thread : threads) {
            thread.join();
        }
        return counter.value();
    }

    private static final class MutableCounter {
        private int value;

        void addUnsafe(int delta) {
            int current = value;
            if ((current & 7) == 0) {
                Thread.yield();
            }
            value = current + delta;
        }

        synchronized void addSafe(int delta) {
            value += delta;
        }

        int value() {
            return value;
        }
    }

    public record RaceConditionResult(
            int threadCount,
            int iterationsPerThread,
            int expectedValue,
            int unsafeValue,
            int safeValue
    ) {
    }
}
