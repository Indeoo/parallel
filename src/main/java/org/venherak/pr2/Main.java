package org.venherak.pr2;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws Exception {
        Project2Config config = Project2Config.fromArgs(args);
        new Project2Runner(config).run();
    }
}
