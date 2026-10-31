package com.jemdb.benchmark.arraylist;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/** Concurrent read benchmark using an immutable ArrayList and worker-local queues. */
public final class ListConcurrentBenchmark {

  private static final String[] WORKLOADS = {
    "Toyota sorted by price",
    "Electric SUVs count only",
    "Electric SUVs by mileage",
    "Volkswagen top 100 by price"
  };

  private static final Comparator<Car> PRICE_TOP_ORDER =
      Comparator.<Car>comparingInt(Car::price)
          .reversed()
          .thenComparing(Comparator.comparingInt(Car::id).reversed());
  private static final Comparator<Car> MILEAGE_TOP_ORDER =
      Comparator.<Car>comparingInt(Car::mileage)
          .reversed()
          .thenComparing(Comparator.comparingInt(Car::id).reversed());

  private final List<Car> cars;

  private ListConcurrentBenchmark(List<Car> cars) {
    this.cars = cars;
  }

  public static void main(String[] args) throws Exception {
    Path path = BenchmarkSupport.dataPath(args);
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

    ListConcurrentBenchmark benchmark = new ListConcurrentBenchmark(cars);
    ConcurrentBenchmarkSupport.run(
        "Java ArrayList<Car>", rows, loadNanos, WORKLOADS, benchmark::createWorker);
  }

  private ConcurrentBenchmarkSupport.ConcurrentWorker createWorker() {
    return new Worker();
  }

  private final class Worker implements ConcurrentBenchmarkSupport.ConcurrentWorker {
    private final PriorityQueue<Car> priceTop =
        new PriorityQueue<>(BenchmarkSupport.TOP_RESULTS + 1, PRICE_TOP_ORDER);
    private final PriorityQueue<Car> mileageTop =
        new PriorityQueue<>(BenchmarkSupport.TOP_RESULTS + 1, MILEAGE_TOP_ORDER);
    private final PriorityQueue<Car> volkswagenPriceTop =
        new PriorityQueue<>(BenchmarkWorkloads.VOLKSWAGEN_RESULTS + 1, PRICE_TOP_ORDER);

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
      priceTop.clear();
      int matches = 0;
      for (int i = 0, size = cars.size(); i < size; i++) {
        Car car = cars.get(i);
        if (car.makeId() == CarCatalog.TOYOTA
            && car.price() >= BenchmarkWorkloads.TOYOTA_MIN_PRICE
            && car.price() <= BenchmarkWorkloads.TOYOTA_MAX_PRICE
            && car.year() >= BenchmarkWorkloads.TOYOTA_MIN_YEAR
            && car.mileage() <= BenchmarkWorkloads.TOYOTA_MAX_MILEAGE
            && car.automatic()) {
          matches++;
          priceTop.offer(car);
          if (priceTop.size() > BenchmarkSupport.TOP_RESULTS) {
            priceTop.poll();
          }
        }
      }
      long checksum = 0;
      for (Car car : priceTop) {
        checksum += car.price();
      }
      return ConcurrentBenchmarkSupport.fingerprint(matches, priceTop.size(), checksum);
    }

    private long electricSuvCount() {
      int matches = 0;
      for (int i = 0, size = cars.size(); i < size; i++) {
        if (isElectricSuvMatch(cars.get(i))) {
          matches++;
        }
      }
      return ConcurrentBenchmarkSupport.fingerprint(matches, 0, matches);
    }

    private long electricSuvsByMileage() {
      mileageTop.clear();
      int matches = 0;
      for (int i = 0, size = cars.size(); i < size; i++) {
        Car car = cars.get(i);
        if (isElectricSuvMatch(car)) {
          matches++;
          mileageTop.offer(car);
          if (mileageTop.size() > BenchmarkSupport.TOP_RESULTS) {
            mileageTop.poll();
          }
        }
      }
      long checksum = 0;
      for (Car car : mileageTop) {
        checksum += car.mileage();
      }
      return ConcurrentBenchmarkSupport.fingerprint(matches, mileageTop.size(), checksum);
    }

    private long volkswagenTopByPrice() {
      volkswagenPriceTop.clear();
      int matches = 0;
      for (int i = 0, size = cars.size(); i < size; i++) {
        Car car = cars.get(i);
        if (car.makeId() == CarCatalog.VOLKSWAGEN
            && car.year() >= BenchmarkWorkloads.VOLKSWAGEN_MIN_YEAR
            && car.year() <= BenchmarkWorkloads.VOLKSWAGEN_MAX_YEAR
            && car.price() >= BenchmarkWorkloads.VOLKSWAGEN_MIN_PRICE
            && car.price() <= BenchmarkWorkloads.VOLKSWAGEN_MAX_PRICE) {
          matches++;
          volkswagenPriceTop.offer(car);
          if (volkswagenPriceTop.size() > BenchmarkWorkloads.VOLKSWAGEN_RESULTS) {
            volkswagenPriceTop.poll();
          }
        }
      }
      long checksum = 0;
      for (Car car : volkswagenPriceTop) {
        checksum += car.id();
        checksum += car.model().hashCode();
        checksum += car.city().hashCode();
      }
      return ConcurrentBenchmarkSupport.fingerprint(
          matches, volkswagenPriceTop.size(), checksum);
    }

    private boolean isElectricSuvMatch(Car car) {
      return car.fuelId() == CarCatalog.ELECTRIC
          && car.bodyId() == CarCatalog.SUV
          && car.year() >= BenchmarkWorkloads.ELECTRIC_SUV_MIN_YEAR
          && car.price() <= BenchmarkWorkloads.ELECTRIC_SUV_MAX_PRICE;
    }
  }
}
