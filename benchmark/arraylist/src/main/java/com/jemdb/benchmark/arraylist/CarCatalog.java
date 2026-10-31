package com.jemdb.benchmark.arraylist;

import java.util.Set;

final class CarCatalog {

  static final String[] MAKES = {
    "Toyota", "Volkswagen", "Ford", "BMW", "Mercedes-Benz", "Tesla",
    "Honda", "Volvo", "Audi", "Chevrolet", "Peugeot", "Skoda", "Nissan",
    "Hyundai", "Kia", "Mazda", "Subaru", "Lexus", "Porsche",
    "Land Rover", "Jaguar", "Renault", "Citroen", "Fiat", "SEAT", "MINI",
    "Opel", "Dacia", "Alfa Romeo", "Mitsubishi", "Suzuki", "Dodge", "Jeep",
    "GMC", "Cadillac", "Buick", "Chrysler", "Ram", "Acura", "Infiniti",
    "Genesis", "Rivian", "Polestar", "Cupra"
  };

  static final String[][] MODELS = {
    {"Corolla", "Camry", "RAV4", "Prius"},
    {"Golf", "Passat", "Tiguan", "ID.4"},
    {"Focus", "Mustang", "Escape", "F-150"},
    {"3 Series", "5 Series", "X3", "X5"},
    {"A-Class", "C-Class", "E-Class", "GLC"},
    {"Model 3", "Model Y", "Model S", "Model X"},
    {"Civic", "Accord", "CR-V", "HR-V"},
    {"V40", "S60", "XC40", "XC60"},
    {"A3", "A4", "Q3", "Q5"},
    {"Malibu", "Camaro", "Equinox", "Silverado"},
    {"208", "308", "3008", "5008"},
    {"Fabia", "Octavia", "Karoq", "Kodiaq"},
    {"Sentra", "Altima", "Rogue", "Pathfinder"},
    {"Elantra", "Sonata", "Tucson", "Santa Fe"},
    {"Forte", "K5", "Sportage", "Sorento"},
    {"Mazda3", "Mazda6", "CX-5", "CX-90"},
    {"Impreza", "Legacy", "Forester", "Outback"},
    {"IS", "ES", "NX", "RX"},
    {"718 Cayman", "911", "Macan", "Cayenne"},
    {"Discovery", "Defender", "Range Rover Evoque", "Range Rover Sport"},
    {"XE", "XF", "E-Pace", "F-Pace"},
    {"Clio", "Megane", "Captur", "Austral"},
    {"C3", "C4", "C5 Aircross", "Berlingo"},
    {"500", "Panda", "Tipo", "500X"},
    {"Ibiza", "Leon", "Ateca", "Tarraco"},
    {"Cooper", "Countryman", "Clubman", "Paceman"},
    {"Corsa", "Astra", "Mokka", "Insignia"},
    {"Sandero", "Duster", "Jogger", "Logan"},
    {"Giulia", "Stelvio", "Giulietta", "Tonale"},
    {"Mirage", "Lancer", "Outlander", "Eclipse Cross"},
    {"Swift", "Vitara", "S-Cross", "Jimny"},
    {"Charger", "Challenger", "Durango", "Hornet"},
    {"Wrangler", "Cherokee", "Compass", "Grand Cherokee"},
    {"Terrain", "Acadia", "Sierra", "Yukon"},
    {"CT5", "XT4", "XT5", "Escalade"},
    {"Encore", "Envision", "Regal", "Enclave"},
    {"300", "Pacifica", "Voyager", "Sebring"},
    {"1500", "2500", "ProMaster", "Rebel"},
    {"Integra", "TLX", "RDX", "MDX"},
    {"Q50", "Q60", "QX50", "QX60"},
    {"G70", "G80", "GV70", "GV80"},
    {"R1T", "R1S"},
    {"2", "3", "4"},
    {"Born", "Formentor", "Leon", "Tavascan"}
  };

  static final String[] CITIES = {
    "Los Angeles", "New York", "Chicago", "Copenhagen", "Berlin", "Paris",
    "London", "Madrid", "Rome", "Amsterdam", "Stockholm", "Prague",
    "Vienna", "Brussels", "Zurich", "Oslo", "Helsinki", "Warsaw",
    "Boston", "Seattle", "Miami", "Austin"
  };

  static final Set<String> SUV_MODELS =
      Set.of(
          "RAV4", "Tiguan", "ID.4", "Escape", "X3", "X5", "GLC", "Model Y",
          "Model X", "CR-V", "HR-V", "XC40", "XC60", "Q3", "Q5", "Equinox",
          "3008", "5008", "Karoq", "Kodiaq", "Rogue", "Pathfinder", "Tucson",
          "Santa Fe", "Sportage", "Sorento", "CX-5", "CX-90", "Forester",
          "Outback", "NX", "RX", "Macan", "Cayenne", "Discovery", "Defender",
          "Range Rover Evoque", "Range Rover Sport", "E-Pace", "F-Pace", "Captur",
          "Austral", "C5 Aircross", "500X", "Ateca", "Tarraco", "Countryman",
          "Paceman", "Mokka", "Duster", "Stelvio", "Tonale", "Outlander",
          "Eclipse Cross", "Vitara", "S-Cross", "Jimny", "Durango", "Hornet",
          "Wrangler", "Cherokee", "Compass", "Grand Cherokee", "Terrain", "Acadia",
          "Yukon", "XT4", "XT5", "Escalade", "Encore", "Envision", "Enclave",
          "RDX", "MDX", "QX50", "QX60", "GV70", "GV80", "R1S", "Formentor",
          "Tavascan");

  static final Set<String> PICKUP_MODELS =
      Set.of("F-150", "Silverado", "Sierra", "1500", "2500", "Rebel", "R1T");

  static final Set<String> COUPE_MODELS =
      Set.of("Mustang", "Camaro", "718 Cayman", "911", "Challenger", "Q60");

  static final int TOYOTA = 0;
  static final int VOLKSWAGEN = 1;
  static final int TESLA = 5;
  static final int RIVIAN = 41;
  static final int POLESTAR = 42;
  static final int ELECTRIC = 3;
  static final int SUV = 1;

  private CarCatalog() {}
}
