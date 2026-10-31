package com.jemdb.benchmark.elasticsearch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

class ElasticsearchProfileContextTest {

  @Test
  void elasticsearchProfileStartsWithTheOfficialClient() throws IOException {
    HttpServer elasticsearch =
        HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    elasticsearch.createContext("/cars/_count", ElasticsearchProfileContextTest::sendCount);
    elasticsearch.start();

    String url = "http://127.0.0.1:" + elasticsearch.getAddress().getPort();
    try (ConfigurableApplicationContext context =
        new SpringApplicationBuilder(ServiceBenchmarkApplication.class)
            .web(WebApplicationType.NONE)
            .profiles("elasticsearch")
            .run(
                "--search.elasticsearch-url=" + url,
                "--search.elasticsearch-index=cars")) {
      assertEquals("elasticsearch", context.getBean(SearchBackend.class).name());
      assertEquals(1_000_000, context.getBean(SearchBackend.class).rowCount());
    } finally {
      elasticsearch.stop(0);
    }
  }

  private static void sendCount(HttpExchange exchange) throws IOException {
    byte[] body =
        "{\"count\":1000000,\"_shards\":{\"total\":1,\"successful\":1,\"skipped\":0,\"failed\":0}}"
            .getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.getResponseHeaders().add("X-Elastic-Product", "Elasticsearch");
    exchange.sendResponseHeaders(200, body.length);
    exchange.getResponseBody().write(body);
    exchange.close();
  }
}
