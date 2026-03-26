package org.venherak.lab3;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws Exception {
        Lab3Config config = Lab3Config.fromArgs(args);
        new Lab3Runner(config).run();
    }
}
