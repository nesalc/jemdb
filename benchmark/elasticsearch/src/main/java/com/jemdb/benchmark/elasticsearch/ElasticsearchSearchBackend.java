package com.jemdb.benchmark.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("elasticsearch")
final class ElasticsearchSearchBackend implements SearchBackend, AutoCloseable {

  private final ElasticsearchClient client;
  private final int rowCount;
  private final SearchRequest[] requests;

  @Autowired
  ElasticsearchSearchBackend(
      @Value("${search.elasticsearch-url}") String elasticsearchUrl,
      @Value("${search.elasticsearch-index}") String index) {
    this(ElasticsearchClient.of(builder -> builder.host(elasticsearchUrl)), index);
  }

  ElasticsearchSearchBackend(ElasticsearchClient client, String index) {
    this.client = client;
    this.requests = buildRequests(index);
    this.rowCount = readCount(index);
    System.out.printf("Connected to Elasticsearch index '%s' with %,d cars%n", index, rowCount);
  }

  @Override
  public String name() {
    return "elasticsearch";
  }

  @Override
  public int rowCount() {
    return rowCount;
  }

  @Override
  public CarSearchResponse search(Workload workload) {
    SearchResponse<CarView> response;
    try {
      response = client.search(requests[workload.ordinal()], CarView.class);
    } catch (IOException e) {
      throw new IllegalStateException("Elasticsearch search failed", e);
    }

    if (response.hits().total() == null) {
      throw new IllegalStateException("Elasticsearch response has no exact total");
    }
    int totalMatches = Math.toIntExact(response.hits().total().value());
    if (workload == Workload.ELECTRIC_COUNT) {
      return new CarSearchResponse(totalMatches, 0, totalMatches, List.of());
    }

    List<Hit<CarView>> hits = response.hits().hits();
    List<CarView> cars = new ArrayList<>(hits.size());
    long checksum = 0;
    for (Hit<CarView> hit : hits) {
      CarView car = hit.source();
      if (car == null) {
        throw new IllegalStateException("Elasticsearch hit has no _source");
      }
      cars.add(car);
      checksum += switch (workload) {
        case TOYOTA -> car.price();
        case ELECTRIC_MILEAGE -> car.mileage();
        case VOLKSWAGEN, VOLKSWAGEN_EXPORT ->
            (long) car.id() + car.model().hashCode() + car.city().hashCode();
        case ELECTRIC_COUNT -> throw new IllegalStateException("Count query returned rows");
      };
    }
    return new CarSearchResponse(totalMatches, cars.size(), checksum, cars);
  }

  @Override
  public void close() throws IOException {
    client.close();
  }

  private int readCount(String index) {
    try {
      CountRequest request = CountRequest.of(builder -> builder.index(index));
      return Math.toIntExact(client.count(request).count());
    } catch (IOException e) {
      throw new IllegalStateException("Could not count the Elasticsearch index", e);
    }
  }

  private static SearchRequest[] buildRequests(String index) {
    String[] bodies = queryBodies();
    SearchRequest[] result = new SearchRequest[bodies.length];
    for (int i = 0; i < bodies.length; i++) {
      String body = bodies[i];
      result[i] =
          SearchRequest.of(
              builder ->
                  builder
                      .withJson(new StringReader(body))
                      .index(index)
                      .requestCache(false));
    }
    return result;
  }

  private static String[] queryBodies() {
    return new String[] {
      """
      {
        "size": 50,
        "track_total_hits": true,
        "query": {"bool": {"filter": [
          {"term": {"makeId": 0}},
          {"range": {"price": {"gte": 15000, "lte": 45000}}},
          {"range": {"year": {"gte": 2018}}},
          {"range": {"mileage": {"lte": 80000}}},
          {"term": {"automatic": true}}
        ]}},
        "sort": [{"price": {"order": "asc"}}]
      }
      """,
      """
      {
        "size": 0,
        "track_total_hits": true,
        "query": {"bool": {"filter": [
          {"term": {"fuelId": 3}},
          {"term": {"bodyId": 1}},
          {"range": {"year": {"gte": 2020}}},
          {"range": {"price": {"lte": 80000}}}
        ]}}
      }
      """,
      """
      {
        "size": 50,
        "track_total_hits": true,
        "query": {"bool": {"filter": [
          {"term": {"fuelId": 3}},
          {"term": {"bodyId": 1}},
          {"range": {"year": {"gte": 2020}}},
          {"range": {"price": {"lte": 80000}}}
        ]}},
        "sort": [{"mileage": {"order": "asc"}}]
      }
      """,
      """
      {
        "size": 100,
        "track_total_hits": true,
        "query": {"bool": {"filter": [
          {"term": {"makeId": 1}},
          {"range": {"year": {"gte": 2012, "lte": 2020}}},
          {"range": {"price": {"gte": 10000, "lte": 50000}}}
        ]}},
        "sort": [{"price": {"order": "asc"}}]
      }
      """,
      """
      {
        "size": 10000,
        "track_total_hits": true,
        "query": {"bool": {"filter": [
          {"term": {"makeId": 1}},
          {"range": {"year": {"gte": 2012, "lte": 2020}}},
          {"range": {"price": {"gte": 10000, "lte": 50000}}}
        ]}}
      }
      """
    };
  }
}
