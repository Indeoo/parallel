package org.venherak.lab2;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws Exception {
        Lab2Config config = Lab2Config.fromArgs(args);
        new Lab2Runner(config).run();
    }
}
