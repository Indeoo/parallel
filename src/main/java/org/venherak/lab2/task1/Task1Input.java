package org.venherak.lab2.task1;

import java.nio.file.Path;
import java.util.List;

public record Task1Input(List<Path> htmlFiles, long[] numbers, double[][] matrixA, double[][] matrixB) {
}
