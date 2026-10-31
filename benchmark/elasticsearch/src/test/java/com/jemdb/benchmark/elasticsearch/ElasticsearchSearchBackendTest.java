package com.jemdb.benchmark.elasticsearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ShardStatistics;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class ElasticsearchSearchBackendTest {

  @Test
  void countQueryReturnsExactTotalWithoutRows() throws IOException {
    ElasticsearchClient client = clientWithIndexCount();
    when(client.search(any(SearchRequest.class), eq(CarView.class)))
        .thenReturn(response(16_152, List.of()));
    ElasticsearchSearchBackend backend = new ElasticsearchSearchBackend(client, "cars");

    CarSearchResponse result = backend.search(Workload.ELECTRIC_COUNT);

    assertEquals(16_152, result.totalMatches());
    assertEquals(0, result.returnedRows());
    assertEquals(16_152, result.checksum());
    assertEquals(List.of(), result.cars());
  }

  @Test
  void searchQueryMaterializesTheSameCompleteCarDto() throws IOException {
    CarView car =
        new CarView(
            7, 0, "Toyota", "RAV4", 2024, 32_000, 12_000, 2, 1, true, "Los Angeles");
    ElasticsearchClient client = clientWithIndexCount();
    when(client.search(any(SearchRequest.class), eq(CarView.class)))
        .thenReturn(response(1, List.of(Hit.of(hit -> hit.index("cars").id("7").source(car)))));
    ElasticsearchSearchBackend backend = new ElasticsearchSearchBackend(client, "cars");

    CarSearchResponse result = backend.search(Workload.TOYOTA);

    assertEquals(1, result.totalMatches());
    assertEquals(1, result.returnedRows());
    assertEquals(32_000, result.checksum());
    assertEquals(car, result.cars().get(0));
  }

  private static ElasticsearchClient clientWithIndexCount() throws IOException {
    ElasticsearchClient client = mock(ElasticsearchClient.class);
    when(client.count(any(CountRequest.class)))
        .thenReturn(
            CountResponse.of(
                response -> response.count(1_000_000).shards(successfulShards())));
    return client;
  }

  private static SearchResponse<CarView> response(int total, List<Hit<CarView>> hits) {
    return SearchResponse.of(
        response ->
            response
                .took(1)
                .timedOut(false)
                .shards(successfulShards())
                .hits(
                    HitsMetadata.of(
                        metadata ->
                            metadata
                                .total(
                                    TotalHits.of(
                                        value ->
                                            value.value(total).relation(TotalHitsRelation.Eq)))
                                .hits(hits))));
  }

  private static ShardStatistics successfulShards() {
    return ShardStatistics.of(shards -> shards.total(1).successful(1).failed(0));
  }
}
