package com.jemdb.benchmark.elasticsearch;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/** External HTTP throughput benchmark shared by both Spring search backends. */
public final class HttpConcurrentBenchmark {

  private static final long DEFAULT_WARMUP_MILLIS = 5_000;
  private static final long DEFAULT_MEASURE_MILLIS = 10_000;
  private static final int[] DEFAULT_THREAD_COUNTS = {1, 2, 4, 8};
  private static final Workload[] BENCHMARK_MIX = {
    Workload.TOYOTA,
    Workload.ELECTRIC_COUNT,
    Workload.ELECTRIC_MILEAGE,
    Workload.VOLKSWAGEN
  };
  private static final Expected[] EXPECTED_BY_WORKLOAD = {
    new Expected(1_089, 50, 1_696_638),
    new Expected(16_152, 0, 16_152),
    new Expected(16_152, 50, 4_941),
    new Expected(6_780, 100, -111_957_239_520L),
    new Expected(6_780, 6_780, -8_087_409_039_692L)
  };

  private final HttpClient client =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  private final Workload[] workloads;
  private final HttpRequest[] requests;

  private HttpConcurrentBenchmark(String baseUrl, Workload[] workloads) {
    this.workloads = workloads;
    String normalized = baseUrl.endsWith("/")
        ? baseUrl.substring(0, baseUrl.length() - 1)
        : baseUrl;
    requests = new HttpRequest[workloads.length];
    for (int i = 0; i < workloads.length; i++) {
      requests[i] =
          HttpRequest.newBuilder(URI.create(normalized + workloads[i].path))
              .timeout(Duration.ofMinutes(2))
              .header("Accept", "application/json")
              .GET()
              .build();
    }
  }

  public static void main(String[] args) throws Exception {
    String baseUrl = args.length > 0 ? args[0] : "http://localhost:9081";
    new HttpConcurrentBenchmark(baseUrl, configuredWorkloads()).run(baseUrl);
  }

  private void run(String baseUrl) throws Exception {
    long warmupMillis = positiveLongProperty("benchmark.concurrentWarmupMillis", DEFAULT_WARMUP_MILLIS);
    long measureMillis = positiveLongProperty("benchmark.concurrentMeasureMillis", DEFAULT_MEASURE_MILLIS);
    int[] threadCounts = threadCounts();

    System.out.printf("%nSpring search service at %s%n", baseUrl);
    System.out.printf(
        "Warm-up: %,d ms, measurement: %,d ms%n", warmupMillis, measureMillis);
    for (int i = 0; i < requests.length; i++) {
      ResponseResult result = execute(i);
      requireExpected(i, result);
      System.out.printf(
          "RESULT %-28s matches=%d rows=%d checksum=%d bytes=%,d%n",
          workloads[i].displayName,
          result.totalMatches,
          result.returnedRows,
          result.checksum,
          result.bytes);
    }
    System.out.println(
        "threads | requests/second | scaling | median | p95 | p99 | response MiB/s | checksum");

    double singleThreadThroughput = 0;
    for (int threadCount : threadCounts) {
      ExecutorService executor =
          Executors.newFixedThreadPool(threadCount, namedThreadFactory());
      try {
        runPhase(executor, threadCount, warmupMillis);
        PhaseResult result = runPhase(executor, threadCount, measureMillis);
        double throughput = result.operations * 1_000_000_000d / result.elapsedNanos;
        double mebibytesPerSecond =
            result.bytes * 1_000_000_000d / result.elapsedNanos / 1024d / 1024d;
        if (singleThreadThroughput == 0) {
          singleThreadThroughput = throughput;
        }
        System.out.printf(
            Locale.ROOT,
            "%7d | %,15.0f | %6.2fx | %6.2f ms | %6.2f ms | %6.2f ms | %,14.2f | %d%n",
            threadCount,
            throughput,
            throughput / singleThreadThroughput,
            percentileMillis(result.durations, 0.50),
            percentileMillis(result.durations, 0.95),
            percentileMillis(result.durations, 0.99),
            mebibytesPerSecond,
            result.checksum);
      } finally {
        executor.shutdownNow();
        executor.awaitTermination(10, TimeUnit.SECONDS);
      }
    }
  }

  private PhaseResult runPhase(ExecutorService executor, int threadCount, long durationMillis)
      throws Exception {
    CountDownLatch ready = new CountDownLatch(threadCount);
    CountDownLatch start = new CountDownLatch(1);
    PhaseControl control = new PhaseControl();
    List<Future<WorkerResult>> futures = new ArrayList<>(threadCount);

    for (int workerIndex = 0; workerIndex < threadCount; workerIndex++) {
      int firstWorkload = workerIndex % requests.length;
      futures.add(
          executor.submit(() -> runWorker(firstWorkload, ready, start, control)));
    }

    ready.await();
    long phaseStart = System.nanoTime();
    control.deadlineNanos = phaseStart + TimeUnit.MILLISECONDS.toNanos(durationMillis);
    start.countDown();

    long operations = 0;
    long bytes = 0;
    long checksum = 0;
    List<WorkerResult> workerResults = new ArrayList<>(futures.size());
    for (Future<WorkerResult> future : futures) {
      WorkerResult result = get(future);
      workerResults.add(result);
      operations += result.operations;
      bytes += result.bytes;
      checksum += result.checksum;
    }
    if (operations > Integer.MAX_VALUE) {
      throw new IllegalStateException("Too many latency samples: " + operations);
    }
    long[] durations = new long[(int) operations];
    int durationIndex = 0;
    for (WorkerResult result : workerResults) {
      System.arraycopy(result.durations, 0, durations, durationIndex, result.durations.length);
      durationIndex += result.durations.length;
    }
    Arrays.sort(durations);
    return new PhaseResult(
        operations, bytes, System.nanoTime() - phaseStart, checksum, durations);
  }

  private WorkerResult runWorker(
      int firstWorkload,
      CountDownLatch ready,
      CountDownLatch start,
      PhaseControl control)
      throws Exception {
    ready.countDown();
    start.await();

    long operations = 0;
    long bytes = 0;
    long checksum = 0;
    LongSamples durations = new LongSamples();
    int workload = firstWorkload;
    while (System.nanoTime() < control.deadlineNanos) {
      long requestStart = System.nanoTime();
      ResponseResult result = execute(workload);
      durations.add(System.nanoTime() - requestStart);
      requireExpected(workload, result);
      checksum = Long.rotateLeft(checksum, 7) ^ result.fingerprint();
      bytes += result.bytes;
      operations++;
      workload++;
      if (workload == requests.length) {
        workload = 0;
      }
    }
    return new WorkerResult(operations, bytes, checksum, durations.toArray());
  }

  private ResponseResult execute(int workload) throws Exception {
    HttpResponse<byte[]> response =
        client.send(requests[workload], HttpResponse.BodyHandlers.ofByteArray());
    if (response.statusCode() != 200) {
      throw new IllegalStateException(
          "HTTP " + response.statusCode() + " from " + requests[workload].uri());
    }
    byte[] body = response.body();
    return new ResponseResult(
        parseLong(body, "totalMatches"),
        parseLong(body, "returnedRows"),
        parseLong(body, "checksum"),
        body.length);
  }

  private void requireExpected(int workload, ResponseResult actual) {
    Workload selected = workloads[workload];
    Expected expected = EXPECTED_BY_WORKLOAD[selected.ordinal()];
    if (actual.totalMatches != expected.totalMatches
        || actual.returnedRows != expected.returnedRows
        || actual.checksum != expected.checksum) {
      throw new IllegalStateException(
          "Unexpected result for " + selected.displayName
              + ": expected " + expected + " but got " + actual);
    }
  }

  private static Workload[] configuredWorkloads() {
    String configured = System.getProperty("benchmark.workloads");
    if (configured == null || configured.isBlank()) {
      return BENCHMARK_MIX.clone();
    }
    String[] names = configured.split(",");
    Workload[] result = new Workload[names.length];
    for (int i = 0; i < names.length; i++) {
      result[i] = Workload.valueOf(names[i].trim().toUpperCase(Locale.ROOT));
    }
    return result;
  }

  private static long parseLong(byte[] json, String field) {
    byte[] marker = ("\"" + field + "\":").getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    int start = indexOf(json, marker);
    if (start < 0) {
      throw new IllegalStateException("Missing JSON field '" + field + "'");
    }
    int index = start + marker.length;
    boolean negative = index < json.length && json[index] == '-';
    if (negative) {
      index++;
    }
    long value = 0;
    int digits = 0;
    while (index < json.length && json[index] >= '0' && json[index] <= '9') {
      value = Math.addExact(Math.multiplyExact(value, 10), json[index] - '0');
      index++;
      digits++;
    }
    if (digits == 0) {
      throw new IllegalStateException("JSON field '" + field + "' is not an integer");
    }
    return negative ? -value : value;
  }

  private static int indexOf(byte[] value, byte[] pattern) {
    outer:
    for (int i = 0; i <= value.length - pattern.length; i++) {
      for (int j = 0; j < pattern.length; j++) {
        if (value[i + j] != pattern[j]) {
          continue outer;
        }
      }
      return i;
    }
    return -1;
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

  private static long positiveLongProperty(String name, long defaultValue) {
    long value = Long.getLong(name, defaultValue);
    if (value <= 0) {
      throw new IllegalArgumentException(name + " must be greater than zero");
    }
    return value;
  }

  private static double percentileMillis(long[] sortedDurations, double percentile) {
    if (sortedDurations.length == 0) {
      return 0;
    }
    int index = Math.min(
        sortedDurations.length - 1,
        Math.max(0, (int) Math.ceil(sortedDurations.length * percentile) - 1));
    return sortedDurations[index] / 1_000_000d;
  }

  private static ThreadFactory namedThreadFactory() {
    return new ThreadFactory() {
      private int nextId;

      @Override
      public synchronized Thread newThread(Runnable task) {
        return new Thread(task, "http-benchmark-" + nextId++);
      }
    };
  }

  private static final class PhaseControl {
    private volatile long deadlineNanos;
  }

  private static final class LongSamples {
    private long[] values = new long[1_024];
    private int size;

    void add(long value) {
      if (size == values.length) {
        values = Arrays.copyOf(values, values.length * 2);
      }
      values[size++] = value;
    }

    long[] toArray() {
      return Arrays.copyOf(values, size);
    }
  }

  private record Expected(long totalMatches, long returnedRows, long checksum) {}

  private record ResponseResult(
      long totalMatches, long returnedRows, long checksum, long bytes) {
    long fingerprint() {
      long result = checksum * 0x9e3779b97f4a7c15L;
      result += totalMatches * 0xc2b2ae3d27d4eb4fL;
      result ^= returnedRows * 0x165667b19e3779f9L;
      return result ^ (result >>> 31);
    }
  }

  private record WorkerResult(long operations, long bytes, long checksum, long[] durations) {}

  private record PhaseResult(
      long operations, long bytes, long elapsedNanos, long checksum, long[] durations) {}
}
