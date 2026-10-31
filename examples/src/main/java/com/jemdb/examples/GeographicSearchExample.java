package com.jemdb.examples;

import com.jemdb.DataException;
import com.jemdb.EnumField;
import com.jemdb.IntField;
import com.jemdb.JemDb;
import com.jemdb.JemDbConfig;
import com.jemdb.LocationField;
import com.jemdb.Query;
import com.jemdb.SimpleGeoPoint;
import com.jemdb.SimpleRowData;
import com.jemdb.StringField;

public final class GeographicSearchExample {

    private GeographicSearchExample() {
    }

    public static void main(String[] args) throws DataException {
        var db = new JemDb(JemDbConfig.builder()
                .addStringField("id")
                .addStringField("name")
                .addEnumField("country")
                .addRangeIntField("population", 0, 20_000_000, 100_000)
                .addLatLngField("location", 7)
                .setPrimaryKey("id")
                .setPartitionKey("location")
                .build());

        var id = db.stringField("id");
        var name = db.stringField("name");
        var country = db.enumField("country");
        var population = db.intField("population");
        var location = db.locationField("location");

        addCity(db, id, name, country, population, location,
                "CPH", "Copenhagen", "Denmark", 1_396_508, 55.6761, 12.5683);
        addCity(db, id, name, country, population, location,
                "AAR", "Aarhus", "Denmark", 301_049, 56.1629, 10.2039);
        addCity(db, id, name, country, population, location,
                "ODE", "Odense", "Denmark", 182_387, 55.4038, 10.4024);
        addCity(db, id, name, country, population, location,
                "BER", "Berlin", "Germany", 3_878_100, 52.5200, 13.4050);
        addCity(db, id, name, country, population, location,
                "STO", "Stockholm", "Sweden", 984_748, 59.3293, 18.0686);

        // Map bounds are south latitude, west longitude, north latitude, east longitude.
        var visibleMapArea = Query.builder()
                .within(location, 54.5, 7.0, 58.0, 15.5)
                .sortBy(population, Query.SortOrder.DESCENDING)
                .limit(20)
                .build();

        try (var results = db.query(visibleMapArea)) {
            System.out.printf("Cities inside the map (%d matches):%n", results.totalMatches());
            while (results.next()) {
                System.out.printf("- %-10s %,9d people  (%.4f, %.4f)%n",
                        results.get(name),
                        results.get(population),
                        results.latitude(location),
                        results.longitude(location));
            }
        }
    }

    private static void addCity(
            JemDb db,
            StringField id,
            StringField name,
            EnumField country,
            IntField population,
            LocationField location,
            String idValue,
            String nameValue,
            String countryValue,
            int populationValue,
            double latitude,
            double longitude) throws DataException {
        db.upsert(new SimpleRowData(db.getFieldCount())
                .setString(id, idValue)
                .setString(name, nameValue)
                .setString(country, countryValue)
                .setInt(population, populationValue)
                .setGeoPoint(location, new SimpleGeoPoint(latitude, longitude)));
    }
}
