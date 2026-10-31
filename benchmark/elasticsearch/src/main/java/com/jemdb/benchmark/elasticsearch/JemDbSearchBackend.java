package com.jemdb.benchmark.elasticsearch;

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
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!elasticsearch")
final class JemDbSearchBackend implements SearchBackend {

  private final JemDb db;
  private final int rowCount;
  private final IntField id;
  private final IntField makeId;
  private final EnumField make;
  private final StringField model;
  private final IntField year;
  private final IntField price;
  private final IntField mileage;
  private final IntField fuelId;
  private final IntField bodyId;
  private final BooleanField automatic;
  private final StringField city;
  private final Query[] queries;
  private final ThreadLocal<QueryCursor> cursors;

  JemDbSearchBackend(@Value("${search.data-path}") String dataPath) throws Exception {
    db = new JemDb(schema());
    id = db.intField("id");
    makeId = db.intField("makeId");
    make = db.enumField("make");
    model = db.stringField("model");
    year = db.intField("year");
    price = db.intField("price");
    mileage = db.intField("mileage");
    fuelId = db.intField("fuelId");
    bodyId = db.intField("bodyId");
    automatic = db.booleanField("automatic");
    city = db.stringField("city");

    SimpleRowData row = new SimpleRowData(db.getFieldCount());
    long start = System.nanoTime();
    rowCount =
        CarCsv.read(
            Path.of(dataPath),
            car -> {
              row.setInt(id, car.id())
                  .setInt(makeId, car.makeId())
                  .setString(make, car.make())
                  .setString(model, car.model())
                  .setInt(year, car.year())
                  .setInt(price, car.price())
                  .setInt(mileage, car.mileage())
                  .setInt(fuelId, car.fuelId())
                  .setInt(bodyId, car.bodyId())
                  .setBoolean(automatic, car.automatic())
                  .setString(city, car.city());
              db.upsert(row);
            });
    System.out.printf(
        "Loaded %,d cars into JemDB in %,.2f ms%n",
        rowCount, (System.nanoTime() - start) / 1_000_000d);

    queries = buildQueries();
    cursors = ThreadLocal.withInitial(db::createQueryCursor);
  }

  @Override
  public String name() {
    return "jemdb";
  }

  @Override
  public int rowCount() {
    return rowCount;
  }

  @Override
  public CarSearchResponse search(Workload workload) {
    QueryCursor cursor = cursors.get();
    try {
      db.query(queries[workload.ordinal()], cursor);
      int matches = cursor.totalMatches();
      if (workload == Workload.ELECTRIC_COUNT) {
        return new CarSearchResponse(matches, 0, matches, List.of());
      }

      List<CarView> cars = new ArrayList<>(cursor.size());
      long checksum = 0;
      while (cursor.next()) {
        CarView car = readCar(cursor);
        cars.add(car);
        checksum += switch (workload) {
          case TOYOTA -> car.price();
          case ELECTRIC_MILEAGE -> car.mileage();
          case VOLKSWAGEN, VOLKSWAGEN_EXPORT ->
              (long) car.id() + car.model().hashCode() + car.city().hashCode();
          case ELECTRIC_COUNT -> throw new IllegalStateException("Count query returned rows");
        };
      }
      return new CarSearchResponse(matches, cars.size(), checksum, cars);
    } finally {
      cursor.close();
    }
  }

  private CarView readCar(QueryCursor cursor) {
    return new CarView(
        cursor.get(id),
        cursor.get(makeId),
        cursor.get(make),
        cursor.get(model),
        cursor.get(year),
        cursor.get(price),
        cursor.get(mileage),
        cursor.get(fuelId),
        cursor.get(bodyId),
        cursor.get(automatic),
        cursor.get(city));
  }

  private Query[] buildQueries() {
    return new Query[] {
      Query.builder()
          .where(make, BenchmarkConstants.TOYOTA_MAKE)
          .whereBetween(
              price, BenchmarkConstants.TOYOTA_MIN_PRICE, BenchmarkConstants.TOYOTA_MAX_PRICE)
          .whereAtLeast(year, BenchmarkConstants.TOYOTA_MIN_YEAR)
          .whereAtMost(mileage, BenchmarkConstants.TOYOTA_MAX_MILEAGE)
          .where(automatic, true)
          .sortBy(price, Query.SortOrder.ASCENDING)
          .limit(BenchmarkConstants.TOP_RESULTS)
          .build(),
      Query.builder()
          .where(fuelId, BenchmarkConstants.ELECTRIC)
          .where(bodyId, BenchmarkConstants.SUV)
          .whereAtLeast(year, BenchmarkConstants.ELECTRIC_SUV_MIN_YEAR)
          .whereAtMost(price, BenchmarkConstants.ELECTRIC_SUV_MAX_PRICE)
          .limit(0)
          .build(),
      Query.builder()
          .where(fuelId, BenchmarkConstants.ELECTRIC)
          .where(bodyId, BenchmarkConstants.SUV)
          .whereAtLeast(year, BenchmarkConstants.ELECTRIC_SUV_MIN_YEAR)
          .whereAtMost(price, BenchmarkConstants.ELECTRIC_SUV_MAX_PRICE)
          .sortBy(mileage, Query.SortOrder.ASCENDING)
          .limit(BenchmarkConstants.TOP_RESULTS)
          .build(),
      Query.builder()
          .where(make, BenchmarkConstants.VOLKSWAGEN_MAKE)
          .whereBetween(
              year,
              BenchmarkConstants.VOLKSWAGEN_MIN_YEAR,
              BenchmarkConstants.VOLKSWAGEN_MAX_YEAR)
          .whereBetween(
              price,
              BenchmarkConstants.VOLKSWAGEN_MIN_PRICE,
              BenchmarkConstants.VOLKSWAGEN_MAX_PRICE)
          .sortBy(price, Query.SortOrder.ASCENDING)
          .limit(BenchmarkConstants.VOLKSWAGEN_RESULTS)
          .build(),
      Query.builder()
          .where(make, BenchmarkConstants.VOLKSWAGEN_MAKE)
          .whereBetween(
              year,
              BenchmarkConstants.VOLKSWAGEN_MIN_YEAR,
              BenchmarkConstants.VOLKSWAGEN_MAX_YEAR)
          .whereBetween(
              price,
              BenchmarkConstants.VOLKSWAGEN_MIN_PRICE,
              BenchmarkConstants.VOLKSWAGEN_MAX_PRICE)
          .build()
    };
  }

  private static JemDbConfig schema() {
    JemDbConfig.Builder builder =
        JemDbConfig.builder()
            .setName("cars-for-sale-service-benchmark")
            .addIntField("id")
            .addIntField("makeId")
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
}
