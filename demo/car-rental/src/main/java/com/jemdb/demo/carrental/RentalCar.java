package com.jemdb.demo.carrental;

public record RentalCar(
    String vehicleCode,
    String pickupCountry,
    String pickupCity,
    String make,
    String model,
    String category,
    int seats,
    int bags,
    boolean automatic,
    boolean electric,
    int dailyPriceEur) {}
