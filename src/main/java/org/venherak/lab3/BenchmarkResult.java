package org.venherak.lab3;

public record BenchmarkResult(
        String taskName,
        String mode,
        int threads,
        long elapsedNanos,
        String summary
) {
    public double elapsedMillis() {
        return elapsedNanos / 1_000_000.0;
    }
}
