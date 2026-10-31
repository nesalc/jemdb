package com.jemdb.demo.carrental;

import com.jemdb.BooleanField;
import com.jemdb.DataException;
import com.jemdb.IntField;
import com.jemdb.JemDb;
import com.jemdb.JemDbConfig;
import com.jemdb.Query;
import com.jemdb.QueryCursor;
import com.jemdb.RowCursor;
import com.jemdb.SimpleRowData;
import com.jemdb.StringField;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class RentalCarSearchService {

  private static final String[] COUNTRIES = {"", "DE", "DK", "ES", "FR", "IT", "UK", "US"};
  private static final String[] MAKES = {
    "", "Audi", "BMW", "Chevrolet", "Fiat", "Ford", "Honda", "Jeep", "Mercedes-Benz",
    "MINI", "Nissan", "Peugeot", "Renault", "SEAT", "Skoda", "Tesla", "Toyota",
    "Volkswagen", "Volvo"
  };
  private static final String[] CATEGORIES = {
    "", "Economy", "Compact", "SUV", "Premium", "Convertible", "Van"
  };

  private final JemDb db;
  private final StringField vehicleCode;
  private final IntField pickupCountry;
  private final StringField pickupCity;
  private final IntField make;
  private final StringField model;
  private final IntField category;
  private final IntField seats;
  private final IntField bags;
  private final BooleanField automatic;
  private final BooleanField electric;
  private final IntField dailyPrice;

  public RentalCarSearchService() {
    JemDbConfig schema =
        JemDbConfig.builder()
            .setName("rental-cars")
            .addStringField("vehicleCode")
            .addSearchableIntField("pickupCountry")
            .addStringField("pickupCity")
            .addSearchableIntField("make")
            .addStringField("model")
            .addSearchableIntField("category")
            .addRangeIntField("seats", 1, 10, 1)
            .addRangeIntField("bags", 0, 8, 1)
            .addBooleanField("automatic")
            .addBooleanField("electric")
            .addRangeIntField("dailyPrice", 0, 300, 10)
            .setPrimaryKey("vehicleCode")
            .build();

    db = new JemDb(schema);
    vehicleCode = db.stringField("vehicleCode");
    pickupCountry = db.intField("pickupCountry");
    pickupCity = db.stringField("pickupCity");
    make = db.intField("make");
    model = db.stringField("model");
    category = db.intField("category");
    seats = db.intField("seats");
    bags = db.intField("bags");
    automatic = db.booleanField("automatic");
    electric = db.booleanField("electric");
    dailyPrice = db.intField("dailyPrice");

    loadSampleInventory();
  }

  public List<RentalCar> search(CarSearch search) {
    Query.Builder query = Query.builder().limit(search.limit());
    addNamedFilter(query, pickupCountry, "pickupCountry", search.pickupCountries(), COUNTRIES);
    addNamedFilter(query, make, "make", search.makes(), MAKES);
    addNamedFilter(query, category, "category", search.categories(), CATEGORIES);
    addIntFilter(query, seats, "seats", search.seats());
    addIntFilter(query, bags, "bags", search.bags());
    addBooleanFilter(query, automatic, "automatic", search.automatic());
    addBooleanFilter(query, electric, "electric", search.electric());
    addIntFilter(query, dailyPrice, "dailyPrice", search.dailyPrice());

    List<RentalCar> result = new ArrayList<>(Math.min(search.limit(), 64));
    try (QueryCursor cursor = db.query(query.build())) {
      while (cursor.next()) {
        result.add(toRentalCar(cursor));
      }
    }
    return result;
  }

  public Optional<RentalCar> findByVehicleCode(String requestedVehicleCode) {
    RowCursor row = db.createRowCursor();
    return db.findByPrimaryKey(requestedVehicleCode, row)
        ? Optional.of(toRentalCar(row))
        : Optional.empty();
  }

  public int size() {
    return db.getDbRowCount();
  }

  private static void addNamedFilter(
      Query.Builder query,
      IntField field,
      String name,
      List<String> filters,
      String[] knownValues) {
    if (filters.isEmpty()) {
      return;
    }
    if (filters.size() == 1) {
      query.where(field, codeFor(name, filters.get(0), knownValues));
      return;
    }

    int[] codes = new int[filters.size()];
    for (int i = 0; i < filters.size(); i++) {
      codes[i] = codeFor(name, filters.get(i), knownValues);
    }
    query.whereAny(field, codes);
  }

  private static int codeFor(String name, String requestedValue, String[] knownValues) {
    String value = requestedValue.trim();
    for (int code = 1; code < knownValues.length; code++) {
      if (knownValues[code].equalsIgnoreCase(value)) {
        return code;
      }
    }
    throw new IllegalArgumentException("unknown " + name + " '" + requestedValue + "'");
  }

  private static void addIntFilter(
      Query.Builder query, IntField field, String name, List<String> filters) {
    if (filters.isEmpty()) {
      return;
    }

    if (filters.size() > 1) {
      int[] values = new int[filters.size()];
      for (int i = 0; i < filters.size(); i++) {
        String filter = filters.get(i).trim();
        if (filter.contains("..")) {
          throw new IllegalArgumentException(
              name + " cannot combine repeated values and ranges");
        }
        values[i] = parseInt(name, filter);
      }
      query.whereAny(field, values);
      return;
    }

    String filter = filters.get(0).trim();
    int separator = filter.indexOf("..");
    if (separator < 0) {
      query.where(field, parseInt(name, filter));
      return;
    }
    if (separator != filter.lastIndexOf("..")) {
      throw invalidRange(name, filter);
    }

    String lower = filter.substring(0, separator).trim();
    String upper = filter.substring(separator + 2).trim();
    if (lower.isEmpty() && upper.isEmpty()) {
      throw invalidRange(name, filter);
    }
    if (lower.isEmpty()) {
      query.whereAtMost(field, parseInt(name, upper));
    } else if (upper.isEmpty()) {
      query.whereAtLeast(field, parseInt(name, lower));
    } else {
      query.whereBetween(field, parseInt(name, lower), parseInt(name, upper));
    }
  }

  private static void addBooleanFilter(
      Query.Builder query, BooleanField field, String name, List<String> filters) {
    if (filters.isEmpty()) {
      return;
    }
    if (filters.size() > 1) {
      throw new IllegalArgumentException(name + " accepts exactly one value");
    }
    query.where(field, parseBoolean(name, filters.get(0)));
  }

  private static int parseInt(String name, String value) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException(name + " must contain integer values", exception);
    }
  }

  private static boolean parseBoolean(String name, String value) {
    return switch (value.trim().toLowerCase()) {
      case "true", "1" -> true;
      case "false", "0" -> false;
      default -> throw new IllegalArgumentException(name + " must be true or false");
    };
  }

  private static IllegalArgumentException invalidRange(String name, String value) {
    return new IllegalArgumentException(
        name + " has invalid range '" + value + "'; use min..max, min.., or ..max");
  }

  private void loadSampleInventory() {
    add("DE-BER-001", "DE", "Berlin", "Volkswagen", "Golf", "Compact", 5, 3, true, false, 58);
    add("DE-BER-002", "DE", "Berlin", "BMW", "3 Series", "Premium", 5, 3, true, false, 104);
    add("DE-MUC-001", "DE", "Munich", "Mercedes-Benz", "C-Class", "Premium", 5, 3, true, false, 112);
    add("ES-AGP-001", "ES", "Malaga", "SEAT", "Ibiza", "Economy", 5, 2, false, false, 39);
    add("ES-AGP-002", "ES", "Malaga", "Peugeot", "3008", "SUV", 5, 4, true, false, 72);
    add("ES-BCN-001", "ES", "Barcelona", "Tesla", "Model 3", "Premium", 5, 3, true, true, 89);
    add("DK-CPH-001", "DK", "Copenhagen", "Volvo", "XC40", "SUV", 5, 4, true, false, 86);
    add("DK-CPH-002", "DK", "Copenhagen", "Tesla", "Model Y", "SUV", 5, 4, true, true, 99);
    add("DK-BLL-001", "DK", "Billund", "Skoda", "Octavia", "Compact", 5, 4, false, false, 61);
    add("FR-CDG-001", "FR", "Paris", "Peugeot", "208", "Economy", 5, 2, false, false, 42);
    add("FR-CDG-002", "FR", "Paris", "Renault", "Clio", "Economy", 5, 2, true, false, 48);
    add("FR-NCE-001", "FR", "Nice", "Audi", "A4", "Premium", 5, 3, true, false, 109);
    add("IT-FCO-001", "IT", "Rome", "Fiat", "500", "Economy", 4, 1, false, false, 37);
    add("IT-FCO-002", "IT", "Rome", "Volkswagen", "T-Cross", "SUV", 5, 3, true, false, 67);
    add("UK-LHR-001", "UK", "London", "MINI", "Cooper", "Compact", 4, 2, true, false, 71);
    add("UK-LHR-002", "UK", "London", "Nissan", "Qashqai", "SUV", 5, 4, true, false, 82);
    add("UK-LHR-003", "UK", "London", "Mercedes-Benz", "Vito", "Van", 8, 6, true, false, 138);
    add("US-LAX-001", "US", "Los Angeles", "Toyota", "Corolla", "Compact", 5, 3, true, false, 54);
    add("US-LAX-002", "US", "Los Angeles", "Ford", "Mustang", "Convertible", 4, 2, true, false, 119);
    add("US-LAX-003", "US", "Los Angeles", "Tesla", "Model Y", "SUV", 5, 4, true, true, 97);
    add("US-MIA-001", "US", "Miami", "Jeep", "Wrangler", "SUV", 5, 3, true, false, 94);
    add("US-MIA-002", "US", "Miami", "Chevrolet", "Tahoe", "SUV", 7, 5, true, false, 126);
    add("US-SFO-001", "US", "San Francisco", "Ford", "Mustang Mach-E", "SUV", 5, 4, true, true, 108);
    add("US-ORD-001", "US", "Chicago", "Honda", "CR-V", "SUV", 5, 4, true, false, 79);
  }

  private void add(
      String code,
      String countryName,
      String cityName,
      String makeName,
      String modelName,
      String categoryName,
      int seatCount,
      int bagCount,
      boolean isAutomatic,
      boolean isElectric,
      int price) {
    SimpleRowData row = new SimpleRowData(db.getFieldCount());
    row.setString(vehicleCode, code)
        .setInt(pickupCountry, codeFor("pickupCountry", countryName, COUNTRIES))
        .setString(pickupCity, cityName)
        .setInt(make, codeFor("make", makeName, MAKES))
        .setString(model, modelName)
        .setInt(category, codeFor("category", categoryName, CATEGORIES))
        .setInt(seats, seatCount)
        .setInt(bags, bagCount)
        .setBoolean(automatic, isAutomatic)
        .setBoolean(electric, isElectric)
        .setInt(dailyPrice, price);
    try {
      db.upsert(row);
    } catch (DataException e) {
      throw new IllegalStateException("Invalid sample car: " + code, e);
    }
  }

  private RentalCar toRentalCar(RowCursor row) {
    return new RentalCar(
        row.get(vehicleCode),
        COUNTRIES[row.get(pickupCountry)],
        row.get(pickupCity),
        MAKES[row.get(make)],
        row.get(model),
        CATEGORIES[row.get(category)],
        row.get(seats),
        row.get(bags),
        row.get(automatic),
        row.get(electric),
        row.get(dailyPrice));
  }
}
