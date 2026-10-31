package com.jemdb.examples;

import com.jemdb.BooleanField;
import com.jemdb.DataException;
import com.jemdb.DoubleField;
import com.jemdb.EnumField;
import com.jemdb.IntField;
import com.jemdb.JemDb;
import com.jemdb.JemDbConfig;
import com.jemdb.Query;
import com.jemdb.SimpleRowData;
import com.jemdb.StringField;

public final class ProductCatalogExample {

    private ProductCatalogExample() {
    }

    public static void main(String[] args) throws DataException {
        var db = new JemDb(JemDbConfig.builder()
                .addStringField("id")
                .addStringField("name")
                .addEnumField("category")
                .addFixedPointSearchableField("price", 2)
                .addRangeIntField("rating", 1, 5, 1)
                .addBooleanField("inStock")
                .setPrimaryKey("id")
                .build());

        var id = db.stringField("id");
        var name = db.stringField("name");
        var category = db.enumField("category");
        var price = db.doubleField("price");
        var rating = db.intField("rating");
        var inStock = db.booleanField("inStock");

        addProduct(db, id, name, category, price, rating, inStock,
                "P-1001", "Wireless headphones", "audio", 129.95, 4, true);
        addProduct(db, id, name, category, price, rating, inStock,
                "P-1002", "Studio speakers", "audio", 399.00, 5, true);
        addProduct(db, id, name, category, price, rating, inStock,
                "P-1003", "USB-C dock", "computers", 179.00, 4, false);
        addProduct(db, id, name, category, price, rating, inStock,
                "P-1004", "Mechanical keyboard", "computers", 89.50, 5, true);
        addProduct(db, id, name, category, price, rating, inStock,
                "P-1005", "Desk lamp", "home", 49.90, 4, true);

        var query = Query.builder()
                .whereAny(category, "audio", "computers")
                .whereBetween(price, 75.00, 200.00)
                .whereAtLeast(rating, 4)
                .where(inStock, true)
                .sortBy(price, Query.SortOrder.ASCENDING)
                .limit(10)
                .build();

        try (var results = db.query(query)) {
            System.out.printf("Found %d matching products:%n", results.totalMatches());
            while (results.next()) {
                System.out.printf("- %-22s $%6.2f  rating %d%n",
                        results.get(name), results.get(price), results.get(rating));
            }
        }

        var product = db.createRowCursor();
        if (db.findByPrimaryKey("P-1002", product)) {
            System.out.printf("%nPrimary-key lookup: %s costs $%.2f%n",
                    product.get(name), product.get(price));
        }
    }

    private static void addProduct(
            JemDb db,
            StringField id,
            StringField name,
            EnumField category,
            DoubleField price,
            IntField rating,
            BooleanField inStock,
            String idValue,
            String nameValue,
            String categoryValue,
            double priceValue,
            int ratingValue,
            boolean inStockValue) throws DataException {
        db.upsert(new SimpleRowData(db.getFieldCount())
                .setString(id, idValue)
                .setString(name, nameValue)
                .setString(category, categoryValue)
                .setDouble(price, priceValue)
                .setInt(rating, ratingValue)
                .setBoolean(inStock, inStockValue));
    }
}
