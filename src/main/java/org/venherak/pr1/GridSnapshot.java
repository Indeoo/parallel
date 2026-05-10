package org.venherak.pr1;

public record GridSnapshot(int step, int[][] counts) {
    public int totalParticles() {
        int total = 0;
        for (int y = 0; y < counts.length; y++) {
            for (int x = 0; x < counts[y].length; x++) {
                total += counts[y][x];
            }
        }
        return total;
    }
}
