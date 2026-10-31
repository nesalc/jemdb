package com.jemdb.benchmark.elasticsearch;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
final class SearchController {

  private final SearchBackend backend;

  SearchController(SearchBackend backend) {
    this.backend = backend;
  }

  @GetMapping("/status")
  Map<String, Object> status() {
    return Map.of("backend", backend.name(), "rows", backend.rowCount());
  }

  @GetMapping("/search/toyota")
  CarSearchResponse toyota() {
    return backend.search(Workload.TOYOTA);
  }

  @GetMapping("/search/electric/count")
  CarSearchResponse electricCount() {
    return backend.search(Workload.ELECTRIC_COUNT);
  }

  @GetMapping("/search/electric")
  CarSearchResponse electricByMileage() {
    return backend.search(Workload.ELECTRIC_MILEAGE);
  }

  @GetMapping("/search/volkswagen")
  CarSearchResponse volkswagen() {
    return backend.search(Workload.VOLKSWAGEN);
  }

  @GetMapping("/search/volkswagen/export")
  CarSearchResponse volkswagenExport() {
    return backend.search(Workload.VOLKSWAGEN_EXPORT);
  }
}
