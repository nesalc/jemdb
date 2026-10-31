package com.jemdb.benchmark.elasticsearch;

public record CarView(
    int id,
    int makeId,
    String make,
    String model,
    int year,
    int price,
    int mileage,
    int fuelId,
    int bodyId,
    boolean automatic,
    String city) {}
