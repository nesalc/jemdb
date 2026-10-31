package com.jemdb.benchmark.arraylist;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/** Shared fixed-duration harness for the concurrent benchmark applications. */
final class ConcurrentBenchmarkSupport {

  private static final long DEFAULT_WARMUP_MILLIS = 2_000;
  private static final long DEFAULT_MEASURE_MILLIS = 5_000;
  private static final int[] DEFAULT_THREAD_COUNTS = {1, 2, 4, 8};

  private ConcurrentBenchmarkSupport() {}

  static void run(
      String implementation,
      int rows,
      long loadNanos,
      String[] workloadNames,
      WorkerFactory workerFactory)
      throws Exception {
    int[] workloadIndexes = workloadIndexes(workloadNames.length);
    String[] selectedWorkloadNames = new String[workloadIndexes.length];
    for (int i = 0; i < workloadIndexes.length; i++) {
      selectedWorkloadNames[i] = workloadNames[workloadIndexes[i]];
    }
    WorkerFactory selectedWorkerFactory =
        () -> new MappedWorker(workerFactory.create(), workloadIndexes);
    long warmupMillis =
        positiveLongProperty("benchmark.concurrentWarmupMillis", DEFAULT_WARMUP_MILLIS);
    long measureMillis =
        positiveLongProperty("benchmark.concurrentMeasureMillis", DEFAULT_MEASURE_MILLIS);
    int[] threadCounts = threadCounts();

    System.out.printf("%n%s concurrent search%n", implementation);
    System.out.printf("Loaded %,d rows in %,.2f ms%n", rows, loadNanos / 1_000_000d);
    System.out.printf(
        "Available processors: %d, max heap: %,.0f MiB%n",
        Runtime.getRuntime().availableProcessors(),
        Runtime.getRuntime().maxMemory() / 1024d / 1024d);
    System.out.printf(
        "Balanced mix: %s%n", String.join(" | ", selectedWorkloadNames));
    System.out.printf(
        "Warm-up: %,d ms, measurement: %,d ms%n", warmupMillis, measureMillis);

    long[] expected;
    ConcurrentWorker verificationWorker = selectedWorkerFactory.create();
    try {
      expected = expectedResults(verificationWorker, selectedWorkloadNames);
    } finally {
      verificationWorker.close();
    }
    for (int i = 0; i < selectedWorkloadNames.length; i++) {
      System.out.printf(
          "RESULT %-28s fingerprint=%d%n", selectedWorkloadNames[i], expected[i]);
    }

    System.out.println(
        "threads | requests/second | scaling | GC collections | GC time | checksum");

    double singleThreadThroughput = 0;
    for (int threadCount : threadCounts) {
      List<ConcurrentWorker> workers = new ArrayList<>(threadCount);
      ExecutorService executor =
          Executors.newFixedThreadPool(threadCount, namedThreadFactory(implementation));
      try {
        for (int i = 0; i < threadCount; i++) {
          workers.add(selectedWorkerFactory.create());
        }

        runPhase(executor, workers, expected, warmupMillis);

        GcSnapshot gcBefore = gcSnapshot();
        PhaseResult measured = runPhase(executor, workers, expected, measureMillis);
        GcSnapshot gcAfter = gcSnapshot();

        double throughput = measured.operations * 1_000_000_000d / measured.elapsedNanos;
        if (singleThreadThroughput == 0) {
          singleThreadThroughput = throughput;
        }
        System.out.printf(
            Locale.ROOT,
            "%7d | %,15.0f | %6.2fx | %14d | %6d ms | %d%n",
            threadCount,
            throughput,
            throughput / singleThreadThroughput,
            gcAfter.collections - gcBefore.collections,
            gcAfter.timeMillis - gcBefore.timeMillis,
            measured.checksum);
      } finally {
        for (ConcurrentWorker worker : workers) {
          worker.close();
        }
        executor.shutdownNow();
        executor.awaitTermination(10, TimeUnit.SECONDS);
      }
    }
  }

  static long fingerprint(int totalMatches, int returnedRows, long checksum) {
    long result = checksum * 0x9e3779b97f4a7c15L;
    result += Integer.toUnsignedLong(totalMatches) * 0xc2b2ae3d27d4eb4fL;
    result ^= Integer.toUnsignedLong(returnedRows) * 0x165667b19e3779f9L;
    result ^= result >>> 29;
    result *= 0x94d049bb133111ebL;
    return result ^ (result >>> 31);
  }

  private static long[] expectedResults(ConcurrentWorker worker, String[] workloadNames) {
    long[] expected = new long[workloadNames.length];
    for (int i = 0; i < expected.length; i++) {
      expected[i] = worker.execute(i);
    }
    return expected;
  }

  private static PhaseResult runPhase(
      ExecutorService executor,
      List<ConcurrentWorker> workers,
      long[] expected,
      long durationMillis)
      throws Exception {
    CountDownLatch ready = new CountDownLatch(workers.size());
    CountDownLatch start = new CountDownLatch(1);
    PhaseControl control = new PhaseControl();
    List<Future<WorkerResult>> futures = new ArrayList<>(workers.size());

    for (int workerIndex = 0; workerIndex < workers.size(); workerIndex++) {
      ConcurrentWorker worker = workers.get(workerIndex);
      int firstWorkload = workerIndex % expected.length;
      futures.add(
          executor.submit(
              () -> runWorker(worker, expected, firstWorkload, ready, start, control)));
    }

    ready.await();
    long phaseStart = System.nanoTime();
    control.deadlineNanos = phaseStart + TimeUnit.MILLISECONDS.toNanos(durationMillis);
    start.countDown();

    long operations = 0;
    long checksum = 0;
    for (Future<WorkerResult> future : futures) {
      WorkerResult result = get(future);
      operations += result.operations;
      checksum += result.checksum;
    }
    return new PhaseResult(operations, System.nanoTime() - phaseStart, checksum);
  }

  private static WorkerResult runWorker(
      ConcurrentWorker worker,
      long[] expected,
      int firstWorkload,
      CountDownLatch ready,
      CountDownLatch start,
      PhaseControl control)
      throws InterruptedException {
    ready.countDown();
    start.await();

    long operations = 0;
    long checksum = 0;
    int workload = firstWorkload;
    while (System.nanoTime() < control.deadlineNanos) {
      long actual = worker.execute(workload);
      if (actual != expected[workload]) {
        throw new IllegalStateException(
            "Concurrent result mismatch for workload " + workload
                + ": expected " + expected[workload] + " but got " + actual);
      }
      checksum = Long.rotateLeft(checksum, 7) ^ actual;
      operations++;
      workload++;
      if (workload == expected.length) {
        workload = 0;
      }
    }
    return new WorkerResult(operations, checksum);
  }

  private static WorkerResult get(Future<WorkerResult> future) throws Exception {
    try {
      return future.get();
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof Exception exception) {
        throw exception;
      }
      throw new RuntimeException(cause);
    }
  }

  private static GcSnapshot gcSnapshot() {
    long collections = 0;
    long timeMillis = 0;
    for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
      if (bean.getCollectionCount() >= 0) {
        collections += bean.getCollectionCount();
      }
      if (bean.getCollectionTime() >= 0) {
        timeMillis += bean.getCollectionTime();
      }
    }
    return new GcSnapshot(collections, timeMillis);
  }

  private static int[] threadCounts() {
    String configured = System.getProperty("benchmark.concurrentThreads");
    if (configured == null || configured.isBlank()) {
      return DEFAULT_THREAD_COUNTS.clone();
    }
    String[] values = configured.split(",");
    int[] result = new int[values.length];
    for (int i = 0; i < values.length; i++) {
      result[i] = Integer.parseInt(values[i].trim());
      if (result[i] <= 0) {
        throw new IllegalArgumentException("Thread counts must be greater than zero");
      }
    }
    return result;
  }

  private static int[] workloadIndexes(int workloadCount) {
    String configured = System.getProperty("benchmark.workloadIndexes");
    if (configured == null || configured.isBlank()) {
      int[] all = new int[workloadCount];
      for (int i = 0; i < all.length; i++) {
        all[i] = i;
      }
      return all;
    }
    String[] values = configured.split(",");
    int[] result = new int[values.length];
    for (int i = 0; i < values.length; i++) {
      result[i] = Integer.parseInt(values[i].trim());
      if (result[i] < 0 || result[i] >= workloadCount) {
        throw new IllegalArgumentException(
            "Workload indexes must be between zero and " + (workloadCount - 1));
      }
    }
    return result;
  }

  private static long positiveLongProperty(String name, long defaultValue) {
    long value = Long.getLong(name, defaultValue);
    if (value <= 0) {
      throw new IllegalArgumentException(name + " must be greater than zero");
    }
    return value;
  }

  private static ThreadFactory namedThreadFactory(String implementation) {
    String prefix = implementation.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
    return new ThreadFactory() {
      private int nextId;

      @Override
      public synchronized Thread newThread(Runnable task) {
        return new Thread(task, prefix + "-benchmark-" + nextId++);
      }
    };
  }

  @FunctionalInterface
  interface WorkerFactory {
    ConcurrentWorker create() throws Exception;
  }

  interface ConcurrentWorker extends AutoCloseable {
    long execute(int workloadIndex);

    @Override
    default void close() {}
  }

  private static final class PhaseControl {
    private volatile long deadlineNanos;
  }

  private record WorkerResult(long operations, long checksum) {}

  private record PhaseResult(long operations, long elapsedNanos, long checksum) {}

  private record GcSnapshot(long collections, long timeMillis) {}

  private record MappedWorker(ConcurrentWorker delegate, int[] workloadIndexes)
      implements ConcurrentWorker {

    @Override
    public long execute(int workloadIndex) {
      return delegate.execute(workloadIndexes[workloadIndex]);
    }

    @Override
    public void close() {
      delegate.close();
    }
  }
}
