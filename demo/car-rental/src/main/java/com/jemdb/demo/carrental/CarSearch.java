package com.jemdb.demo.carrental;

import java.util.List;

record CarSearch(
    List<String> pickupCountries,
    List<String> makes,
    List<String> categories,
    List<String> seats,
    List<String> bags,
    List<String> automatic,
    List<String> electric,
    List<String> dailyPrice,
    int limit) {}
