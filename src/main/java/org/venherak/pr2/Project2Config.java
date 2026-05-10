package org.venherak.pr2;

public final class Project2Config {
    private final String host;
    private final int port;
    private final int connectTimeoutMillis;
    private final int actionDelayMillis;

    private Project2Config(String host, int port, int connectTimeoutMillis, int actionDelayMillis) {
        this.host = host;
        this.port = port;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.actionDelayMillis = actionDelayMillis;
    }

    public static Project2Config fromArgs(String[] args) {
        String host = "127.0.0.1";
        int port = 24_620;
        int connectTimeoutMillis = 2_000;
        int actionDelayMillis = 120;

        for (String arg : args) {
            if (!arg.startsWith("--") || !arg.contains("=")) {
                continue;
            }
            String[] parts = arg.substring(2).split("=", 2);
            String key = parts[0];
            String value = parts[1];
            switch (key) {
                case "host" -> host = value;
                case "port" -> port = Integer.parseInt(value);
                case "connectTimeoutMillis" -> connectTimeoutMillis = Integer.parseInt(value);
                case "actionDelayMillis" -> actionDelayMillis = Integer.parseInt(value);
                default -> {
                }
            }
        }

        return new Project2Config(
                host,
                Math.max(1_024, Math.min(65_000, port)),
                Math.max(100, connectTimeoutMillis),
                Math.max(10, actionDelayMillis)
        );
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public int connectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public int actionDelayMillis() {
        return actionDelayMillis;
    }
}
