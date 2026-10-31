package com.jemdb.examples;

import com.jemdb.DataException;
import com.jemdb.JemDb;
import com.jemdb.JemDbConfig;
import com.jemdb.Query;
import com.jemdb.SimpleRowData;

public final class HelloJemDb {

    private HelloJemDb() {
    }

    public static void main(String[] args) throws DataException {
        var db = new JemDb(JemDbConfig.builder()
                .addStringField("id")
                .addStringField("title")
                .addBooleanField("available")
                .setPrimaryKey("id")
                .build());

        var id = db.stringField("id");
        var title = db.stringField("title");
        var available = db.booleanField("available");

        db.upsert(new SimpleRowData(db.getFieldCount())
                .setString(id, "B-101")
                .setString(title, "The Left Hand of Darkness")
                .setBoolean(available, true));
        db.upsert(new SimpleRowData(db.getFieldCount())
                .setString(id, "B-102")
                .setString(title, "The Dispossessed")
                .setBoolean(available, false));
        db.upsert(new SimpleRowData(db.getFieldCount())
                .setString(id, "B-103")
                .setString(title, "A Wizard of Earthsea")
                .setBoolean(available, true));

        var query = Query.builder()
                .where(available, true)
                .build();

        try (var results = db.query(query)) {
            System.out.println("Available books:");
            while (results.next()) {
                System.out.printf("- %s (%s)%n", results.get(title), results.get(id));
            }
        }
    }
}
