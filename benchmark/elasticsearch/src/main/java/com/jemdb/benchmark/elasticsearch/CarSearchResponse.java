package com.jemdb.benchmark.elasticsearch;

import java.util.List;

public record CarSearchResponse(
    int totalMatches, int returnedRows, long checksum, List<CarView> cars) {}
