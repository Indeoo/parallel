package org.venherak.lab1;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws Exception {
        BenchmarkConfig config = BenchmarkConfig.fromArgs(args);
        new BenchmarkRunner(config, args).run();
    }
}
