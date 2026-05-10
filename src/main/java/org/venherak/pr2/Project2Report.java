package org.venherak.pr2;

import java.util.List;

public record Project2Report(
        long elapsedNanos,
        List<String> serverLog,
        List<String> demoTranscript
) {
    public double elapsedMillis() {
        return elapsedNanos / 1_000_000.0;
    }
}
