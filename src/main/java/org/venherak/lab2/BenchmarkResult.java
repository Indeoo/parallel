package org.venherak.lab2;

public record BenchmarkResult(String taskName, String pattern, int threads, long elapsedNanos, String summary) {
    public double elapsedMillis() {
        return elapsedNanos / 1_000_000.0;
    }
}
