package org.venherak.pr1;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

public final class DeadlockDemo {
    public DeadlockResult run(Duration detectionTimeout) throws InterruptedException {
        boolean deadlockDetected = demonstrateDeadlock(detectionTimeout);
        boolean orderedLockingCompleted = demonstrateOrderedLocking();
        return new DeadlockResult(deadlockDetected, orderedLockingCompleted, detectionTimeout.toMillis());
    }

    private boolean demonstrateDeadlock(Duration detectionTimeout) throws InterruptedException {
        ReentrantLock cellA = new ReentrantLock();
        ReentrantLock cellB = new ReentrantLock();
        CountDownLatch firstLockAcquired = new CountDownLatch(2);

        Thread first = new Thread(() -> lockInOppositeOrder(cellA, cellB, firstLockAcquired), "deadlock-a");
        Thread second = new Thread(() -> lockInOppositeOrder(cellB, cellA, firstLockAcquired), "deadlock-b");
        first.start();
        second.start();

        Thread.sleep(detectionTimeout.toMillis());
        boolean detected = first.isAlive() && second.isAlive();

        first.interrupt();
        second.interrupt();
        first.join(1_000);
        second.join(1_000);
        return detected;
    }

    private void lockInOppositeOrder(
            ReentrantLock firstLock,
            ReentrantLock secondLock,
            CountDownLatch firstLockAcquired
    ) {
        try {
            firstLock.lockInterruptibly();
            firstLockAcquired.countDown();
            firstLockAcquired.await();
            secondLock.lockInterruptibly();
            secondLock.unlock();
            firstLock.unlock();
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            if (secondLock.isHeldByCurrentThread()) {
                secondLock.unlock();
            }
            if (firstLock.isHeldByCurrentThread()) {
                firstLock.unlock();
            }
        }
    }

    private boolean demonstrateOrderedLocking() throws InterruptedException {
        ReentrantLock cellA = new ReentrantLock();
        ReentrantLock cellB = new ReentrantLock();
        AtomicBoolean finished = new AtomicBoolean(true);

        Runnable orderedTask = () -> {
            ReentrantLock first = System.identityHashCode(cellA) < System.identityHashCode(cellB) ? cellA : cellB;
            ReentrantLock second = first == cellA ? cellB : cellA;
            first.lock();
            try {
                second.lock();
                try {
                    TimeUnit.MILLISECONDS.sleep(25);
                } catch (InterruptedException exception) {
                    finished.set(false);
                    Thread.currentThread().interrupt();
                } finally {
                    second.unlock();
                }
            } finally {
                first.unlock();
            }
        };

        Thread first = new Thread(orderedTask, "ordered-a");
        Thread second = new Thread(orderedTask, "ordered-b");
        first.start();
        second.start();
        first.join(1_000);
        second.join(1_000);
        return finished.get() && !first.isAlive() && !second.isAlive();
    }

    public record DeadlockResult(boolean deadlockDetected, boolean orderedLockingCompleted, long timeoutMillis) {
    }
}
