package org.venherak.pr1;

public final class AsciiGridRenderer {
    private static final char[] PALETTE = {' ', '.', ':', '-', '=', '+', '*', '#', '%', '@'};

    private AsciiGridRenderer() {
    }

    public static String render(GridSnapshot snapshot) {
        int[][] counts = snapshot.counts();
        int maxValue = 0;
        for (int y = 0; y < counts.length; y++) {
            for (int x = 0; x < counts[y].length; x++) {
                maxValue = Math.max(maxValue, counts[y][x]);
            }
        }

        StringBuilder builder = new StringBuilder();
        builder.append("Step ").append(snapshot.step())
                .append(", particles=").append(snapshot.totalParticles())
                .append(System.lineSeparator());

        for (int y = 0; y < counts.length; y++) {
            builder.append('|');
            for (int x = 0; x < counts[y].length; x++) {
                builder.append(symbolFor(counts[y][x], maxValue));
            }
            builder.append('|').append(System.lineSeparator());
        }

        return builder.toString();
    }

    private static char symbolFor(int value, int maxValue) {
        if (value <= 0 || maxValue <= 0) {
            return ' ';
        }
        int index = (int) Math.round((double) value * (PALETTE.length - 1) / maxValue);
        return PALETTE[Math.min(PALETTE.length - 1, Math.max(1, index))];
    }
}
