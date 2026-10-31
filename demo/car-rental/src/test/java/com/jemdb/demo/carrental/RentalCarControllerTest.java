package com.jemdb.demo.carrental;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RentalCarControllerTest {

  private final MockMvc mvc =
      MockMvcBuilders.standaloneSetup(
              new RentalCarController(new RentalCarSearchService()))
          .build();

  @Test
  void searchesCarsThroughHttp() throws Exception {
    MvcResult result =
        mvc.perform(
                get("/api/cars")
                    .queryParam("pickupCountry", "US")
                    .queryParam("make", "Tesla", "Ford")
                    .queryParam("electric", "true"))
            .andExpect(status().isOk())
            .andReturn();

    assertThat(result.getResponse().getContentAsString())
        .contains("\"vehicleCode\":\"US-LAX-003\"")
        .contains("\"vehicleCode\":\"US-SFO-001\"")
        .doesNotContain("\"vehicleCode\":\"US-LAX-002\"");
  }

  @Test
  void retrievesOneCarByVehicleCode() throws Exception {
    MvcResult result =
        mvc.perform(get("/api/cars/DE-BER-001"))
            .andExpect(status().isOk())
            .andReturn();

    assertThat(result.getResponse().getContentAsString())
        .contains("\"make\":\"Volkswagen\"")
        .contains("\"model\":\"Golf\"");
  }

  @Test
  void invalidFilterReturnsBadRequest() throws Exception {
    MvcResult result =
        mvc.perform(get("/api/cars").queryParam("dailyPrice", "50..80", "100"))
            .andExpect(status().isBadRequest())
            .andReturn();

    assertThat(result.getResponse().getContentAsString())
        .contains("cannot combine repeated values and ranges");
  }
}
