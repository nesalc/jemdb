package com.jemdb.benchmark.arraylist;

import com.jemdb.BooleanField;
import com.jemdb.EnumField;
import com.jemdb.IntField;
import com.jemdb.JemDb;
import com.jemdb.JemDbConfig;
import com.jemdb.Query;
import com.jemdb.QueryCursor;
import com.jemdb.SimpleRowData;
import com.jemdb.StringField;
import java.nio.file.Path;

/** Concurrent read benchmark using worker-local reusable JemDB cursors. */
public final class JemDbConcurrentBenchmark {

  private static final String[] WORKLOADS = {
    "Toyota sorted by price",
    "Electric SUVs count only",
    "Electric SUVs by mileage",
    "Volkswagen top 100 by price"
  };

  private final JemDb db;
  private final IntField id;
  private final IntField price;
  private final IntField mileage;
  private final StringField model;
  private final StringField city;
  private final Query[] queries;

  private JemDbConcurrentBenchmark(JemDb db) {
    this.db = db;
    id = db.intField("id");
    EnumField make = db.enumField("make");
    model = db.stringField("model");
    IntField year = db.intField("year");
    price = db.intField("price");
    mileage = db.intField("mileage");
    IntField fuelId = db.intField("fuelId");
    IntField bodyId = db.intField("bodyId");
    BooleanField automatic = db.booleanField("automatic");
    city = db.stringField("city");

    queries =
        new Query[] {
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
              .build(),
          Query.builder()
              .where(fuelId, CarCatalog.ELECTRIC)
              .where(bodyId, CarCatalog.SUV)
              .whereAtLeast(year, BenchmarkWorkloads.ELECTRIC_SUV_MIN_YEAR)
              .whereAtMost(price, BenchmarkWorkloads.ELECTRIC_SUV_MAX_PRICE)
              .limit(0)
              .build(),
          Query.builder()
              .where(fuelId, CarCatalog.ELECTRIC)
              .where(bodyId, CarCatalog.SUV)
              .whereAtLeast(year, BenchmarkWorkloads.ELECTRIC_SUV_MIN_YEAR)
              .whereAtMost(price, BenchmarkWorkloads.ELECTRIC_SUV_MAX_PRICE)
              .sortBy(mileage, Query.SortOrder.ASCENDING)
              .limit(BenchmarkSupport.TOP_RESULTS)
              .build(),
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
              .build()
        };
  }

  public static void main(String[] args) throws Exception {
    Path path = BenchmarkSupport.dataPath(args);
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

    JemDbConcurrentBenchmark benchmark = new JemDbConcurrentBenchmark(db);
    ConcurrentBenchmarkSupport.run(
        "JemDB", rows, loadNanos, WORKLOADS, benchmark::createWorker);
  }

  private static JemDbConfig schema() {
    JemDbConfig.Builder builder =
        JemDbConfig.builder()
        .setName("cars-for-sale-concurrent")
        .addIntField("id")
        .addEnumField("make")
        .addRangeIntField("fuelId", 0, 4, 1)
        .addRangeIntField("bodyId", 0, 6, 1)
        .addBooleanField("automatic");
    if (Boolean.getBoolean("benchmark.linearYearFilter")) {
      builder.addSearchableIntField("year");
    } else {
      builder.addRangeIntField("year", 1990, 2031, 1);
    }
    return builder
        .addSearchableIntField("price")
        .addSearchableIntField("mileage")
        .addStringField("model")
        .addStringField("city")
        .setPrimaryKey("id")
        .setPartitionKey("make")
        .build();
  }

  private ConcurrentBenchmarkSupport.ConcurrentWorker createWorker() {
    return new Worker();
  }

  private final class Worker implements ConcurrentBenchmarkSupport.ConcurrentWorker {
    private final QueryCursor cursor = db.createQueryCursor();

    @Override
    public long execute(int workloadIndex) {
      return switch (workloadIndex) {
        case 0 -> toyotaSortedByPrice();
        case 1 -> electricSuvCount();
        case 2 -> electricSuvsByMileage();
        case 3 -> volkswagenTopByPrice();
        default -> throw new IllegalArgumentException("Unknown workload: " + workloadIndex);
      };
    }

    private long toyotaSortedByPrice() {
      db.query(queries[0], cursor);
      int matches = cursor.totalMatches();
      int rows = 0;
      long checksum = 0;
      while (cursor.next()) {
        rows++;
        checksum += cursor.get(price);
      }
      return ConcurrentBenchmarkSupport.fingerprint(matches, rows, checksum);
    }

    private long electricSuvCount() {
      db.query(queries[1], cursor);
      int matches = cursor.totalMatches();
      return ConcurrentBenchmarkSupport.fingerprint(matches, 0, matches);
    }

    private long electricSuvsByMileage() {
      db.query(queries[2], cursor);
      int matches = cursor.totalMatches();
      int rows = 0;
      long checksum = 0;
      while (cursor.next()) {
        rows++;
        checksum += cursor.get(mileage);
      }
      return ConcurrentBenchmarkSupport.fingerprint(matches, rows, checksum);
    }

    private long volkswagenTopByPrice() {
      db.query(queries[3], cursor);
      int matches = cursor.totalMatches();
      int rows = 0;
      long checksum = 0;
      while (cursor.next()) {
        rows++;
        checksum += cursor.get(id);
        checksum += cursor.get(model).hashCode();
        checksum += cursor.get(city).hashCode();
      }
      return ConcurrentBenchmarkSupport.fingerprint(matches, rows, checksum);
    }

    @Override
    public void close() {
      cursor.close();
    }
  }
}
