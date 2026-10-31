package com.jemdb.demo.carrental;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class RentalCarSearchServiceTest {

  private final RentalCarSearchService cars = new RentalCarSearchService();

  @Test
  void loadsAUsefulSampleInventory() {
    assertThat(cars.size()).isEqualTo(24);
  }

  @Test
  void combinesReadableFiltersWithAndLogic() {
    CarSearch search =
        search(
            List.of("US"),
            List.of(),
            List.of("SUV"),
            List.of("5.."),
            List.of("4.."),
            List.of("true"),
            List.of(),
            List.of("..100"));

    assertThat(cars.search(search))
        .extracting(RentalCar::vehicleCode)
        .containsExactly("US-LAX-003", "US-ORD-001");
  }

  @Test
  void repeatedMakesUseOrLogic() {
    CarSearch search =
        search(
            List.of("US"),
            List.of("Tesla", "Ford"),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of("true"),
            List.of());

    assertThat(cars.search(search))
        .extracting(RentalCar::vehicleCode)
        .containsExactly("US-LAX-003", "US-SFO-001");
  }

  @Test
  void looksUpCarByVehicleCode() {
    assertThat(cars.findByVehicleCode("DE-BER-001"))
        .get()
        .extracting(RentalCar::make, RentalCar::model)
        .containsExactly("Volkswagen", "Golf");
    assertThat(cars.findByVehicleCode("missing")).isEmpty();
  }

  @Test
  void rejectsUnknownNames() {
    assertThatThrownBy(
            () ->
                cars.search(
                    search(
                        List.of(),
                        List.of("Trabant"),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown make");
  }

  @Test
  void rejectsRepeatedBooleanValues() {
    assertThatThrownBy(
            () ->
                cars.search(
                    search(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of("true", "false"),
                        List.of(),
                        List.of())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("accepts exactly one value");
  }

  private static CarSearch search(
      List<String> countries,
      List<String> makes,
      List<String> categories,
      List<String> seats,
      List<String> bags,
      List<String> automatic,
      List<String> electric,
      List<String> dailyPrice) {
    return new CarSearch(
        countries, makes, categories, seats, bags, automatic, electric, dailyPrice, 50);
  }
}
