package com.jemdb.benchmark.elasticsearch;

final class BenchmarkConstants {

  static final String TOYOTA_MAKE = "Toyota";
  static final String VOLKSWAGEN_MAKE = "Volkswagen";
  static final int ELECTRIC = 3;
  static final int SUV = 1;
  static final int TOP_RESULTS = 50;
  static final int VOLKSWAGEN_RESULTS = 100;

  static final int TOYOTA_MIN_PRICE = 15_000;
  static final int TOYOTA_MAX_PRICE = 45_000;
  static final int TOYOTA_MIN_YEAR = 2018;
  static final int TOYOTA_MAX_MILEAGE = 80_000;

  static final int ELECTRIC_SUV_MIN_YEAR = 2020;
  static final int ELECTRIC_SUV_MAX_PRICE = 80_000;

  static final int VOLKSWAGEN_MIN_YEAR = 2012;
  static final int VOLKSWAGEN_MAX_YEAR = 2020;
  static final int VOLKSWAGEN_MIN_PRICE = 10_000;
  static final int VOLKSWAGEN_MAX_PRICE = 50_000;

  private BenchmarkConstants() {}
}
