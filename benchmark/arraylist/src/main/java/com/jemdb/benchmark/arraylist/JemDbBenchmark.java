package com.jemdb.benchmark.arraylist;

import com.jemdb.BooleanField;
import com.jemdb.EnumField;
import com.jemdb.IntField;
import com.jemdb.JemDb;
import com.jemdb.JemDbConfig;
import com.jemdb.Query;
import com.jemdb.QueryCursor;
import com.jemdb.RowCursor;
import com.jemdb.SimpleRowData;
import com.jemdb.StringField;
import java.nio.file.Path;
import com.jemdb.benchmark.arraylist.BenchmarkSupport.QueryOutcome;

/** Benchmark using the public API from the protected JemDB JAR. */
public final class JemDbBenchmark {

  private final JemDb db;
  private final IntField id;
  private final IntField price;
  private final IntField mileage;
  private final IntField year;
  private final StringField model;
  private final StringField city;
  private final Query toyotaQuery;
  private final Query electricSuvFilterQuery;
  private final Query electricSuvQuery;
  private final Query volkswagenQuery;
  private final RowCursor lookupCursor;

  private JemDbBenchmark(JemDb db) {
    this.db = db;
    id = db.intField("id");
    EnumField make = db.enumField("make");
    model = db.stringField("model");
    year = db.intField("year");
    price = db.intField("price");
    mileage = db.intField("mileage");
    IntField fuelId = db.intField("fuelId");
    IntField bodyId = db.intField("bodyId");
    BooleanField automatic = db.booleanField("automatic");
    city = db.stringField("city");
    lookupCursor = db.createRowCursor();

    toyotaQuery =
        Query.builder()
            .where(make, CarCatalog.MAKES[CarCatalog.TOYOTA])
            .whereBetween(
                price,
                BenchmarkWorkloads.TOYOTA_MIN_PRICE,
                BenchmarkWorkloads.TOYOTA_MAX_PRICE)
            .whereAtLeast(year, BenchmarkWorkloads.TOYOTA_MIN_YEAR)
            .whereAtMost(mileage, BenchmarkWorkloads.TOYOTA_MAX_MILEAGE)
            .where(automatic, true)
            .sortBy(price, Query.SortOrder.ASCENDING)
            .limit(BenchmarkSupport.TOP_RESULTS)
            .build();
    electricSuvFilterQuery =
        Query.builder()
            .where(fuelId, CarCatalog.ELECTRIC)
            .where(bodyId, CarCatalog.SUV)
            .whereAtLeast(year, BenchmarkWorkloads.ELECTRIC_SUV_MIN_YEAR)
            .whereAtMost(price, BenchmarkWorkloads.ELECTRIC_SUV_MAX_PRICE)
            .limit(0)
            .build();
    electricSuvQuery =
        Query.builder()
            .where(fuelId, CarCatalog.ELECTRIC)
            .where(bodyId, CarCatalog.SUV)
            .whereAtLeast(year, BenchmarkWorkloads.ELECTRIC_SUV_MIN_YEAR)
            .whereAtMost(price, BenchmarkWorkloads.ELECTRIC_SUV_MAX_PRICE)
            .sortBy(mileage, Query.SortOrder.ASCENDING)
            .limit(BenchmarkSupport.TOP_RESULTS)
            .build();
    volkswagenQuery =
        Query.builder()
            .where(make, CarCatalog.MAKES[CarCatalog.VOLKSWAGEN])
            .whereBetween(
                year,
                BenchmarkWorkloads.VOLKSWAGEN_MIN_YEAR,
                BenchmarkWorkloads.VOLKSWAGEN_MAX_YEAR)
            .whereBetween(
                price,
                BenchmarkWorkloads.VOLKSWAGEN_MIN_PRICE,
                BenchmarkWorkloads.VOLKSWAGEN_MAX_PRICE)
            .sortBy(price, Query.SortOrder.ASCENDING)
            .limit(BenchmarkWorkloads.VOLKSWAGEN_RESULTS)
            .build();
  }

  public static void main(String[] args) throws Exception {
    Path path = BenchmarkSupport.dataPath(args);
    long baselineHeapBytes = BenchmarkSupport.retainedHeapBytes();
    JemDb db = new JemDb(schema());
    IntField id = db.intField("id");
    EnumField make = db.enumField("make");
    StringField model = db.stringField("model");
    IntField year = db.intField("year");
    IntField price = db.intField("price");
    IntField mileage = db.intField("mileage");
    IntField fuelId = db.intField("fuelId");
    IntField bodyId = db.intField("bodyId");
    BooleanField automatic = db.booleanField("automatic");
    StringField city = db.stringField("city");
    SimpleRowData row = new SimpleRowData(db.getFieldCount());

    long loadStart = System.nanoTime();
    int rows =
        CarCsv.read(
            path,
            (carId, carMakeId, carMake, carModel, carYear, carPrice, carMileage,
                carFuelId, carBodyId, carAutomatic, carCity) -> {
              row.setInt(id, carId)
                  .setString(make, carMake)
                  .setString(model, carModel)
                  .setInt(year, carYear)
                  .setInt(price, carPrice)
                  .setInt(mileage, carMileage)
                  .setInt(fuelId, carFuelId)
                  .setInt(bodyId, carBodyId)
                  .setBoolean(automatic, carAutomatic)
                  .setString(city, carCity);
              db.upsert(row);
            });
    long loadNanos = System.nanoTime() - loadStart;

    JemDbBenchmark benchmark = new JemDbBenchmark(db);
    long loadedHeapBytes = BenchmarkSupport.retainedHeapBytes();
    BenchmarkSupport.printHeader("JemDB", rows, loadNanos);
    BenchmarkSupport.printMemoryUsage(baselineHeapBytes, loadedHeapBytes, rows);
    BenchmarkSupport.run("Toyota sorted by price", benchmark::toyotaSortedByPrice);
    BenchmarkSupport.run("Electric SUVs count only", benchmark::electricSuvsFilterOnly);
    BenchmarkSupport.run("Electric SUVs by mileage", benchmark::electricSuvsByMileage);
    BenchmarkSupport.run("Volkswagen top 100 by price", benchmark::volkswagenTopByPrice);
    BenchmarkSupport.run("25 primary-key lookups", benchmark::primaryKeyLookups);
  }

  private static JemDbConfig schema() {
    return JemDbConfig.builder()
        .setName("cars-for-sale")
        .addIntField("id")
        .addEnumField("make")
        .addRangeIntField("fuelId", 0, 4, 1)
        .addRangeIntField("bodyId", 0, 6, 1)
        .addBooleanField("automatic")
        .addRangeIntField("year", 1990, 2031, 1)
        .addSearchableIntField("price")
        .addSearchableIntField("mileage")
        .addStringField("model")
        .addStringField("city")
        .setPrimaryKey("id")
        .setPartitionKey("make")
        .build();
  }

  private QueryOutcome toyotaSortedByPrice() {
    long checksum = 0;
    int rows = 0;
    try (QueryCursor cursor = db.query(toyotaQuery)) {
      int matches = cursor.totalMatches();
      while (cursor.next()) {
        rows++;
        checksum += cursor.get(price);
      }
      return new QueryOutcome(matches, rows, checksum);
    }
  }

  private QueryOutcome electricSuvsByMileage() {
    long checksum = 0;
    int rows = 0;
    try (QueryCursor cursor = db.query(electricSuvQuery)) {
      int matches = cursor.totalMatches();
      while (cursor.next()) {
        rows++;
        checksum += cursor.get(mileage);
      }
      return new QueryOutcome(matches, rows, checksum);
    }
  }

  private QueryOutcome electricSuvsFilterOnly() {
    try (QueryCursor cursor = db.query(electricSuvFilterQuery)) {
      int matches = cursor.totalMatches();
      return new QueryOutcome(matches, 0, matches);
    }
  }

  private QueryOutcome volkswagenTopByPrice() {
    long checksum = 0;
    int rows = 0;
    try (QueryCursor cursor = db.query(volkswagenQuery)) {
      int matches = cursor.totalMatches();
      while (cursor.next()) {
        rows++;
        checksum += cursor.get(id);
        checksum += cursor.get(model).hashCode();
        checksum += cursor.get(city).hashCode();
      }
      return new QueryOutcome(matches, rows, checksum);
    }
  }

  private QueryOutcome primaryKeyLookups() {
    int matches = 0;
    long checksum = 0;
    for (int lookupId : BenchmarkWorkloads.LOOKUP_IDS) {
      if (db.findByPrimaryKey(lookupId, lookupCursor)) {
        matches++;
        checksum += lookupCursor.get(year);
        checksum += lookupCursor.get(price);
        checksum += lookupCursor.get(model).hashCode();
      }
    }
    return new QueryOutcome(matches, matches, checksum);
  }
}
