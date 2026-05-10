package org.venherak.pr1;

import java.util.List;

public record SimulationResult(
        String mode,
        int width,
        int height,
        int particleCount,
        int steps,
        int snapshotInterval,
        long elapsedNanos,
        List<GridSnapshot> snapshots,
        int finalParticleTotal
) {
    public double elapsedMillis() {
        return elapsedNanos / 1_000_000.0;
    }
}
