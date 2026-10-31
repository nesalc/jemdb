package com.jemdb.benchmark.arraylist;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

final class BenchmarkSupport {

  static final int WARMUP_ROUNDS = Integer.getInteger("benchmark.warmup", 300);
  static final int MEASURED_ROUNDS = Integer.getInteger("benchmark.rounds", 1_000);
  static final int TOP_RESULTS = 50;
  private static final int MEMORY_COLLECTION_ROUNDS = 5;

  private BenchmarkSupport() {}

  static Path dataPath(String[] args) {
    Path path = Path.of(args.length > 0 ? args[0] : "../data/cars-1m.csv.gz");
    if (!Files.isRegularFile(path)) {
      throw new IllegalArgumentException(
          "CSV file not found: " + path + ". Run CarCsvGenerator first.");
    }
    return path;
  }

  static void printHeader(String implementation, int rows, long loadNanos) {
    System.out.printf("%n%s%n", implementation);
    System.out.printf("Loaded %,d rows in %,.2f ms%n", rows, loadNanos / 1_000_000d);
    System.out.printf(
        "Warm-up rounds: %,d, measured rounds: %,d%n", WARMUP_ROUNDS, MEASURED_ROUNDS);
  }

  static long retainedHeapBytes() {
    MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
    long minimumUsedBytes = Long.MAX_VALUE;
    for (int i = 0; i < MEMORY_COLLECTION_ROUNDS; i++) {
      System.gc();
      System.runFinalization();
      try {
        Thread.sleep(50L);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while measuring retained heap", e);
      }
      minimumUsedBytes = Math.min(minimumUsedBytes, memory.getHeapMemoryUsage().getUsed());
    }
    return minimumUsedBytes;
  }

  static void printMemoryUsage(long baselineBytes, long loadedBytes, int rows) {
    long datasetBytes = Math.max(0L, loadedBytes - baselineBytes);
    System.out.printf(
        "Retained heap: baseline %,.2f MiB | loaded %,.2f MiB | dataset %,.2f MiB | %,.1f bytes/row%n",
        toMebibytes(baselineBytes),
        toMebibytes(loadedBytes),
        toMebibytes(datasetBytes),
        datasetBytes / (double) rows);
  }

  private static double toMebibytes(long bytes) {
    return bytes / (1024d * 1024d);
  }

  static QueryOutcome run(String name, BenchmarkOperation operation) {
    QueryOutcome expected = operation.execute();
    for (int i = 0; i < WARMUP_ROUNDS; i++) {
      requireSame(name, expected, operation.execute());
    }

    long[] durations = new long[MEASURED_ROUNDS];
    for (int i = 0; i < durations.length; i++) {
      long start = System.nanoTime();
      QueryOutcome actual = operation.execute();
      durations[i] = System.nanoTime() - start;
      requireSame(name, expected, actual);
    }
    Arrays.sort(durations);
    long total = 0;
    for (long duration : durations) {
      total += duration;
    }
    long median = durations[durations.length / 2];
    long p95 = durations[Math.min(durations.length - 1, (int) Math.ceil(durations.length * 0.95) - 1)];
    System.out.printf(
        "%-28s median %,9.2f us | p95 %,9.2f us | average %,9.2f us%n",
        name,
        median / 1_000d,
        p95 / 1_000d,
        total / (double) durations.length / 1_000d);
    System.out.printf(
        "RESULT %-21s matches=%d rows=%d checksum=%d%n",
        name,
        expected.totalMatches(),
        expected.returnedRows(),
        expected.checksum());
    return expected;
  }

  private static void requireSame(String name, QueryOutcome expected, QueryOutcome actual) {
    if (!expected.equals(actual)) {
      throw new IllegalStateException(
          "Unstable result for " + name + ": expected " + expected + " but got " + actual);
    }
  }

  @FunctionalInterface
  interface BenchmarkOperation {
    QueryOutcome execute();
  }

  record QueryOutcome(int totalMatches, int returnedRows, long checksum) {}
}
