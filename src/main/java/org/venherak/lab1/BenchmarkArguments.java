package org.venherak.lab1;

import java.util.Arrays;

public final class BenchmarkArguments {
    private BenchmarkArguments() {
    }

    public static int intOption(String[] args, String key, int defaultValue) {
        String value = optionValue(args, key);
        return value == null ? defaultValue : Integer.parseInt(value);
    }

    public static long[] longArrayOption(String[] args, String key, long[] defaultValue) {
        String value = optionValue(args, key);
        if (value == null) {
            return defaultValue;
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .mapToLong(Long::parseLong)
                .toArray();
    }

    private static String optionValue(String[] args, String key) {
        String prefix = "--" + key + "=";
        for (String arg : args) {
            if (arg.startsWith(prefix)) {
                return arg.substring(prefix.length());
            }
        }
        return null;
    }
}
