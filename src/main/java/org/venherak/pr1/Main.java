package org.venherak.pr1;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws Exception {
        Project1Config config = Project1Config.fromArgs(args);
        new Project1Runner(config).run();
    }
}
