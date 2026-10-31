package com.jemdb.benchmark.elasticsearch;

enum Workload {
  TOYOTA("Toyota sorted by price", "/api/search/toyota"),
  ELECTRIC_COUNT("Electric SUVs count only", "/api/search/electric/count"),
  ELECTRIC_MILEAGE("Electric SUVs by mileage", "/api/search/electric"),
  VOLKSWAGEN("Volkswagen top 100 by price", "/api/search/volkswagen"),
  VOLKSWAGEN_EXPORT("Volkswagen full export", "/api/search/volkswagen/export");

  final String displayName;
  final String path;

  Workload(String displayName, String path) {
    this.displayName = displayName;
    this.path = path;
  }
}
