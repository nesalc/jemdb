package com.jemdb.demo.carrental;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cars")
public class RentalCarController {

  private final RentalCarSearchService cars;

  public RentalCarController(RentalCarSearchService cars) {
    this.cars = cars;
  }

  @GetMapping
  public List<RentalCar> search(@RequestParam MultiValueMap<String, String> parameters) {
    int limit = parseLimit(parameters.getFirst("limit"));
    if (limit < 0 || limit > 1_000) {
      throw new IllegalArgumentException("limit must be between 0 and 1000");
    }

    return cars.search(
        new CarSearch(
            values(parameters, "pickupCountry"),
            values(parameters, "make"),
            values(parameters, "category"),
            values(parameters, "seats"),
            values(parameters, "bags"),
            values(parameters, "automatic"),
            values(parameters, "electric"),
            values(parameters, "dailyPrice"),
            limit));
  }

  @GetMapping("/{vehicleCode}")
  public ResponseEntity<RentalCar> findByVehicleCode(@PathVariable String vehicleCode) {
    return ResponseEntity.of(cars.findByVehicleCode(vehicleCode));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> invalidQuery(IllegalArgumentException exception) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(Map.of("error", exception.getMessage()));
  }

  private static List<String> values(
      MultiValueMap<String, String> parameters, String parameterName) {
    List<String> values = parameters.get(parameterName);
    return values == null ? List.of() : values;
  }

  private static int parseLimit(String value) {
    if (value == null) {
      return 50;
    }
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("limit must be an integer", exception);
    }
  }
}
