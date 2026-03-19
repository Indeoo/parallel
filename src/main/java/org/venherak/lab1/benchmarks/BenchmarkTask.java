package org.venherak.lab1.benchmarks;

import org.venherak.lab1.BenchmarkConfig;
import org.venherak.lab1.BenchmarkRunner;

import java.util.List;

public interface BenchmarkTask {
    String group();

    String name();

    default List<String> configurationLines() {
        return List.of();
    }

    List<BenchmarkRunner.BenchmarkResult> run(BenchmarkConfig config) throws Exception;
}
