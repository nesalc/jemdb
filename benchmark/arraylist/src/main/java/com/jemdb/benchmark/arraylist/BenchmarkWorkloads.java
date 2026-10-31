package com.jemdb.benchmark.arraylist;

final class BenchmarkWorkloads {

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
  static final int VOLKSWAGEN_RESULTS = 100;

  static final int[] LOOKUP_IDS = {
    37, 40_009, 80_017, 120_011, 160_001, 200_003, 240_007, 280_001, 320_009,
    360_007, 400_009, 440_017, 480_013, 520_021, 560_009, 600_011, 640_007,
    680_003, 720_019, 760_003, 800_011, 840_017, 880_001, 940_001, 999_983
  };

  private BenchmarkWorkloads() {}
}
