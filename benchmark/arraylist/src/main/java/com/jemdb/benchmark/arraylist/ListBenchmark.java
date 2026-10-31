package com.jemdb.benchmark.arraylist;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import com.jemdb.benchmark.arraylist.BenchmarkSupport.QueryOutcome;

/** Baseline using an ordinary ArrayList of immutable Java objects. */
public final class ListBenchmark {

  private final List<Car> cars;

  private ListBenchmark(List<Car> cars) {
    this.cars = cars;
  }

  public static void main(String[] args) throws Exception {
    Path path = BenchmarkSupport.dataPath(args);
    long baselineHeapBytes = BenchmarkSupport.retainedHeapBytes();
    List<Car> cars = new ArrayList<>(1_000_000);
    long loadStart = System.nanoTime();
    int rows =
        CarCsv.read(
            path,
            (id, makeId, make, model, year, price, mileage, fuelId, bodyId, automatic, city) ->
                cars.add(
                    new Car(
                        id,
                        makeId,
                        make,
                        model,
                        year,
                        price,
                        mileage,
                        fuelId,
                        bodyId,
                        automatic,
                        city)));
    long loadNanos = System.nanoTime() - loadStart;

    ListBenchmark benchmark = new ListBenchmark(cars);
    long loadedHeapBytes = BenchmarkSupport.retainedHeapBytes();
    BenchmarkSupport.printHeader("Java ArrayList<Car>", rows, loadNanos);
    BenchmarkSupport.printMemoryUsage(baselineHeapBytes, loadedHeapBytes, rows);
    BenchmarkSupport.run("Toyota sorted by price", benchmark::toyotaSortedByPrice);
    BenchmarkSupport.run("Electric SUVs count only", benchmark::electricSuvsFilterOnly);
    BenchmarkSupport.run("Electric SUVs by mileage", benchmark::electricSuvsByMileage);
    BenchmarkSupport.run("Volkswagen top 100 by price", benchmark::volkswagenTopByPrice);
    BenchmarkSupport.run("25 primary-key lookups", benchmark::primaryKeyLookups);
  }

  private QueryOutcome toyotaSortedByPrice() {
    PriorityQueue<Car> top =
        new PriorityQueue<>(
            Comparator.<Car>comparingInt(Car::price)
                .reversed()
                .thenComparing(Comparator.comparingInt(Car::id).reversed()));
    int matches = 0;
    for (Car car : cars) {
      if (car.makeId() == CarCatalog.TOYOTA
          && car.price() >= BenchmarkWorkloads.TOYOTA_MIN_PRICE
          && car.price() <= BenchmarkWorkloads.TOYOTA_MAX_PRICE
          && car.year() >= BenchmarkWorkloads.TOYOTA_MIN_YEAR
          && car.mileage() <= BenchmarkWorkloads.TOYOTA_MAX_MILEAGE
          && car.automatic()) {
        matches++;
        top.offer(car);
        if (top.size() > BenchmarkSupport.TOP_RESULTS) {
          top.poll();
        }
      }
    }
    long checksum = 0;
    for (Car car : top) {
      checksum += car.price();
    }
    return new QueryOutcome(matches, top.size(), checksum);
  }

  private QueryOutcome electricSuvsByMileage() {
    PriorityQueue<Car> top =
        new PriorityQueue<>(
            Comparator.<Car>comparingInt(Car::mileage)
                .reversed()
                .thenComparing(Comparator.comparingInt(Car::id).reversed()));
    int matches = 0;
    for (Car car : cars) {
      if (isElectricSuvMatch(car)) {
        matches++;
        top.offer(car);
        if (top.size() > BenchmarkSupport.TOP_RESULTS) {
          top.poll();
        }
      }
    }
    long checksum = 0;
    for (Car car : top) {
      checksum += car.mileage();
    }
    return new QueryOutcome(matches, top.size(), checksum);
  }

  private QueryOutcome electricSuvsFilterOnly() {
    int matches = 0;
    for (Car car : cars) {
      if (isElectricSuvMatch(car)) {
        matches++;
      }
    }
    return new QueryOutcome(matches, 0, matches);
  }

  private boolean isElectricSuvMatch(Car car) {
    return car.fuelId() == CarCatalog.ELECTRIC
        && car.bodyId() == CarCatalog.SUV
        && car.year() >= BenchmarkWorkloads.ELECTRIC_SUV_MIN_YEAR
        && car.price() <= BenchmarkWorkloads.ELECTRIC_SUV_MAX_PRICE;
  }

  private QueryOutcome volkswagenTopByPrice() {
    PriorityQueue<Car> top =
        new PriorityQueue<>(
            BenchmarkWorkloads.VOLKSWAGEN_RESULTS + 1,
            Comparator.<Car>comparingInt(Car::price)
                .reversed()
                .thenComparing(Comparator.comparingInt(Car::id).reversed()));
    int matches = 0;
    for (Car car : cars) {
      if (car.makeId() == CarCatalog.VOLKSWAGEN
          && car.year() >= BenchmarkWorkloads.VOLKSWAGEN_MIN_YEAR
          && car.year() <= BenchmarkWorkloads.VOLKSWAGEN_MAX_YEAR
          && car.price() >= BenchmarkWorkloads.VOLKSWAGEN_MIN_PRICE
          && car.price() <= BenchmarkWorkloads.VOLKSWAGEN_MAX_PRICE) {
        matches++;
        top.offer(car);
        if (top.size() > BenchmarkWorkloads.VOLKSWAGEN_RESULTS) {
          top.poll();
        }
      }
    }
    long checksum = 0;
    for (Car car : top) {
      checksum += car.id();
      checksum += car.model().hashCode();
      checksum += car.city().hashCode();
    }
    return new QueryOutcome(matches, top.size(), checksum);
  }

  private QueryOutcome primaryKeyLookups() {
    int matches = 0;
    long checksum = 0;
    for (int id : BenchmarkWorkloads.LOOKUP_IDS) {
      for (Car car : cars) {
        if (car.id() == id) {
          matches++;
          checksum += car.year();
          checksum += car.price();
          checksum += car.model().hashCode();
          break;
        }
      }
    }
    return new QueryOutcome(matches, matches, checksum);
  }
}
