package org.venherak.pr1;

enum Direction {
    UP(0, -1),
    RIGHT(1, 0),
    DOWN(0, 1),
    LEFT(-1, 0);

    private static final Direction[] VALUES = values();

    private final int dx;
    private final int dy;

    Direction(int dx, int dy) {
        this.dx = dx;
        this.dy = dy;
    }

    public int dx() {
        return dx;
    }

    public int dy() {
        return dy;
    }

    public static Direction fromIndex(int index) {
        return VALUES[Math.floorMod(index, VALUES.length)];
    }
}
