package com.jemdb.benchmark.elasticsearch;

interface SearchBackend {
  String name();

  int rowCount();

  CarSearchResponse search(Workload workload);
}
