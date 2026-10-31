package com.jemdb.benchmark.arraylist;

import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.SplittableRandom;
import java.util.zip.GZIPOutputStream;

/** Generates the checked-in benchmark data deterministically. */
public final class CarCsvGenerator {

  private static final long SEED = 0x4a454d4442L;

  private CarCsvGenerator() {}

  public static void main(String[] args) throws Exception {
    Path output = Path.of(args.length > 0 ? args[0] : "../data/cars-1m.csv.gz");
    int rowCount = args.length > 1 ? Integer.parseInt(args[1]) : 1_000_000;
    if (rowCount < 1) {
      throw new IllegalArgumentException("rowCount must be greater than zero");
    }
    Path parent = output.toAbsolutePath().getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }

    SplittableRandom random = new SplittableRandom(SEED);
    try (BufferedWriter writer = openWriter(output)) {
      writer.write(CarCsv.HEADER);
      writer.newLine();
      for (int id = 1; id <= rowCount; id++) {
        writeCar(writer, random, id);
      }
    }
    System.out.printf("Generated %,d deterministic cars in %s%n", rowCount, output);
  }

  private static BufferedWriter openWriter(Path output) throws Exception {
    OutputStream stream = Files.newOutputStream(output);
    if (output.getFileName().toString().endsWith(".gz")) {
      stream = new GZIPOutputStream(stream, 64 * 1024);
    }
    return new BufferedWriter(new OutputStreamWriter(stream, StandardCharsets.UTF_8), 64 * 1024);
  }

  private static void writeCar(BufferedWriter writer, SplittableRandom random, int id)
      throws Exception {
    int makeId = random.nextInt(CarCatalog.MAKES.length);
    String make = CarCatalog.MAKES[makeId];
    String[] availableModels = CarCatalog.MODELS[makeId];
    String model = availableModels[random.nextInt(availableModels.length)];
    int year = 2000 + random.nextInt(27);
    int age = 2026 - year;
    int bodyId = bodyForModel(model, random);
    int fuelId = fuelForCar(makeId, model, year, random);
    int mileage = Math.min(450_000, random.nextInt(5_000 + (age + 1) * 18_000));
    int price = priceForCar(makeId, bodyId, fuelId, year, mileage, random);
    boolean automatic = fuelId == CarCatalog.ELECTRIC || random.nextInt(100) < 78;
    String city = CarCatalog.CITIES[random.nextInt(CarCatalog.CITIES.length)];

    writer.write(
        id
            + ","
            + makeId
            + ","
            + make
            + ","
            + model
            + ","
            + year
            + ","
            + price
            + ","
            + mileage
            + ","
            + fuelId
            + ","
            + bodyId
            + ","
            + automatic
            + ","
            + city);
    writer.newLine();
  }

  private static int bodyForModel(String model, SplittableRandom random) {
    if (CarCatalog.SUV_MODELS.contains(model)) {
      return CarCatalog.SUV;
    }
    if (CarCatalog.PICKUP_MODELS.contains(model)) {
      return 4;
    }
    if (CarCatalog.COUPE_MODELS.contains(model)) {
      return 5;
    }
    return random.nextInt(100) < 45 ? 0 : random.nextInt(2, 4);
  }

  private static int fuelForCar(
      int makeId, String model, int year, SplittableRandom random) {
    if (makeId == CarCatalog.TESLA
        || makeId == CarCatalog.RIVIAN
        || makeId == CarCatalog.POLESTAR
        || model.equals("ID.4")
        || model.equals("Born")
        || model.equals("Tavascan")) {
      return CarCatalog.ELECTRIC;
    }
    if (model.equals("Prius")) {
      return 2;
    }
    int chance = random.nextInt(100);
    if (year >= 2020 && chance < 9) {
      return CarCatalog.ELECTRIC;
    }
    if (year >= 2012 && chance < 27) {
      return 2;
    }
    return chance < 52 ? 1 : 0;
  }

  private static int priceForCar(
      int makeId,
      int bodyId,
      int fuelId,
      int year,
      int mileage,
      SplittableRandom random) {
    int premium = switch (makeId) {
      case 3, 4, 5, 7, 8, 17, 18, 19, 20, 34, 38, 39, 40, 41, 42 -> 13_000;
      default -> 0;
    };
    int bodyPremium = switch (bodyId) {
      case 1, 4 -> 7_000;
      case 5 -> 10_000;
      default -> 0;
    };
    int electricPremium = fuelId == CarCatalog.ELECTRIC ? 8_000 : 0;
    int value =
        8_000
            + (year - 2000) * 1_650
            + premium
            + bodyPremium
            + electricPremium
            + random.nextInt(12_000)
            - mileage / 11;
    return Math.max(2_500, Math.min(150_000, value));
  }
}
