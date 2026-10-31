package com.jemdb.benchmark.elasticsearch;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;

/** Creates a read-optimized Elasticsearch benchmark index from the shared CSV. */
public final class ElasticsearchIndexLoader {

  private static final int BULK_ROWS = 2_000;

  private final HttpClient client =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  private final String baseUrl;
  private final String index;
  private final BulkBuffer bulkBuffer;

  private ElasticsearchIndexLoader(String baseUrl, String index) {
    this.baseUrl = stripTrailingSlash(baseUrl);
    this.index = index;
    this.bulkBuffer = new BulkBuffer();
  }

  public static void main(String[] args) throws Exception {
    String baseUrl = args.length > 0 ? args[0] : "http://localhost:9200";
    String index = args.length > 1 ? args[1] : "cars";
    Path dataPath =
        Path.of(args.length > 2 ? args[2] : "../data/cars-1m.csv.gz");

    ElasticsearchIndexLoader loader = new ElasticsearchIndexLoader(baseUrl, index);
    loader.load(dataPath);
  }

  private void load(Path dataPath) throws Exception {
    requireSuccess("GET", "/", null, "application/json", 200);
    long preparationStart = System.nanoTime();
    deleteExistingIndex();
    createIndex();

    long bulkStart = System.nanoTime();
    int rows = CarCsv.read(dataPath, bulkBuffer::add);
    bulkBuffer.flush();
    long bulkNanos = System.nanoTime() - bulkStart;
    System.out.printf(
        "Bulk indexed %,d cars in %,.2f seconds%n",
        rows, bulkNanos / 1_000_000_000d);

    requireSuccess("POST", "/" + index + "/_refresh", "", "application/json", 200);
    System.out.println("Force-merging the read-only benchmark index to one segment...");
    requireSuccess(
        "POST",
        "/" + index + "/_forcemerge?max_num_segments=1",
        "",
        "application/json",
        200);
    requireSuccess(
        "PUT",
        "/" + index + "/_settings",
        "{\"index\":{\"refresh_interval\":\"1s\"}}",
        "application/json",
        200);

    String countResponse =
        requireSuccess("GET", "/" + index + "/_count?filter_path=count", null, null, 200);
    long indexedRows = parseLongField(countResponse, "count");
    if (indexedRows != rows) {
      throw new IllegalStateException(
          "Expected " + rows + " indexed rows but Elasticsearch reports " + indexedRows);
    }
    System.out.printf("Elasticsearch index '%s' is ready with %,d cars%n", index, indexedRows);
    long preparationNanos = System.nanoTime() - preparationStart;
    System.out.printf(
        "Prepared the searchable index in %,.2f seconds total (%,.2f seconds after bulk indexing)%n",
        preparationNanos / 1_000_000_000d,
        (preparationNanos - bulkNanos) / 1_000_000_000d);
  }

  private void deleteExistingIndex() throws Exception {
    HttpResponse<String> response = send("DELETE", "/" + index, null, null);
    if (response.statusCode() != 200 && response.statusCode() != 404) {
      throw failure("DELETE", "/" + index, response);
    }
  }

  private void createIndex() throws Exception {
    String body =
        """
        {
          "settings": {
            "number_of_shards": 1,
            "number_of_replicas": 0,
            "refresh_interval": "-1"
          },
          "mappings": {
            "properties": {
              "id":        {"type": "integer"},
              "makeId":    {"type": "integer"},
              "make":      {"type": "keyword"},
              "model":     {"type": "keyword"},
              "year":      {"type": "integer"},
              "price":     {"type": "integer"},
              "mileage":   {"type": "integer"},
              "fuelId":    {"type": "integer"},
              "bodyId":    {"type": "integer"},
              "automatic": {"type": "boolean"},
              "city":      {"type": "keyword"}
            }
          }
        }
        """;
    requireSuccess("PUT", "/" + index, body, "application/json", 200);
  }

  private String requireSuccess(
      String method, String path, String body, String contentType, int expectedStatus)
      throws Exception {
    HttpResponse<String> response = send(method, path, body, contentType);
    if (response.statusCode() != expectedStatus) {
      throw failure(method, path, response);
    }
    return response.body();
  }

  private HttpResponse<String> send(String method, String path, String body, String contentType)
      throws Exception {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofMinutes(30));
    if (contentType != null) {
      request.header("Content-Type", contentType);
    }
    if (body == null) {
      request.method(method, HttpRequest.BodyPublishers.noBody());
    } else {
      request.method(
          method,
          HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
    }
    return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private static IllegalStateException failure(
      String method, String path, HttpResponse<String> response) {
    return new IllegalStateException(
        method + " " + path + " returned HTTP " + response.statusCode() + ": " + response.body());
  }

  private static long parseLongField(String json, String field) {
    String marker = "\"" + field + "\":";
    int start = json.indexOf(marker);
    if (start < 0) {
      throw new IllegalStateException("Missing JSON field '" + field + "' in " + json);
    }
    start += marker.length();
    int end = start;
    while (end < json.length() && Character.isDigit(json.charAt(end))) {
      end++;
    }
    return Long.parseLong(json.substring(start, end));
  }

  private static String stripTrailingSlash(String value) {
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }

  private final class BulkBuffer {
    private final StringBuilder body = new StringBuilder(512 * 1024);
    private int bufferedRows;
    private int totalRows;

    void add(CarView car) throws Exception {
      body.append("{\"index\":{\"_id\":\"").append(car.id()).append("\"}}\n");
      appendCar(body, car).append('\n');
      bufferedRows++;
      totalRows++;
      if (bufferedRows == BULK_ROWS) {
        flush();
      }
      if (totalRows % 100_000 == 0) {
        System.out.printf("Submitted %,d cars%n", totalRows);
      }
    }

    void flush() throws Exception {
      if (bufferedRows == 0) {
        return;
      }
      String response =
          requireSuccess(
              "POST",
              "/" + index + "/_bulk?filter_path=errors",
              body.toString(),
              "application/x-ndjson",
              200);
      if (!response.contains("\"errors\":false")) {
        throw new IllegalStateException("Elasticsearch bulk request failed: " + response);
      }
      body.setLength(0);
      bufferedRows = 0;
    }
  }

  private static StringBuilder appendCar(StringBuilder json, CarView car) {
    json.append('{')
        .append("\"id\":").append(car.id())
        .append(",\"makeId\":").append(car.makeId())
        .append(",\"make\":");
    appendQuoted(json, car.make());
    json.append(",\"model\":");
    appendQuoted(json, car.model());
    json.append(",\"year\":").append(car.year())
        .append(",\"price\":").append(car.price())
        .append(",\"mileage\":").append(car.mileage())
        .append(",\"fuelId\":").append(car.fuelId())
        .append(",\"bodyId\":").append(car.bodyId())
        .append(",\"automatic\":").append(car.automatic())
        .append(",\"city\":");
    appendQuoted(json, car.city());
    return json.append('}');
  }

  private static void appendQuoted(StringBuilder json, String value) {
    json.append('"');
    for (int i = 0; i < value.length(); i++) {
      char character = value.charAt(i);
      switch (character) {
        case '"' -> json.append("\\\"");
        case '\\' -> json.append("\\\\");
        case '\n' -> json.append("\\n");
        case '\r' -> json.append("\\r");
        case '\t' -> json.append("\\t");
        default -> json.append(character);
      }
    }
    json.append('"');
  }
}
