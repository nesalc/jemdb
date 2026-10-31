package com.jemdb.benchmark.arraylist;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

final class CarCsv {

  static final String HEADER =
      "id,make_id,make,model,year,price,mileage,fuel_id,body_id,automatic,city";

  private CarCsv() {}

  static int read(Path path, CarConsumer consumer) throws Exception {
    int count = 0;
    try (BufferedReader reader = openReader(path)) {
      String header = reader.readLine();
      if (!HEADER.equals(header)) {
        throw new IOException("Unexpected CSV header in " + path);
      }
      String line;
      while ((line = reader.readLine()) != null) {
        String[] values = line.split(",", -1);
        if (values.length != 11) {
          throw new IOException("Expected 11 columns at data row " + (count + 1));
        }
        consumer.accept(
            Integer.parseInt(values[0]),
            Integer.parseInt(values[1]),
            values[2],
            values[3],
            Integer.parseInt(values[4]),
            Integer.parseInt(values[5]),
            Integer.parseInt(values[6]),
            Integer.parseInt(values[7]),
            Integer.parseInt(values[8]),
            Boolean.parseBoolean(values[9]),
            values[10]);
        count++;
      }
    }
    return count;
  }

  private static BufferedReader openReader(Path path) throws IOException {
    InputStream input = Files.newInputStream(path);
    if (path.getFileName().toString().endsWith(".gz")) {
      input = new GZIPInputStream(input, 64 * 1024);
    }
    return new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8), 64 * 1024);
  }

  @FunctionalInterface
  interface CarConsumer {
    void accept(
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
        String city)
        throws Exception;
  }
}
