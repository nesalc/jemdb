# JemDB

**JemDB** *(pronounced “Gem DB”)* is an embedded, low-latency, in-memory search
engine for structured Java data. The **J** stands for Java.

It is designed for read-heavy applications that need fast filtering, numeric
ranges, bounded sorting, primary-key lookup, and geographic search directly
inside the JVM—without operating a separate search service.

JemDB lives inside your application: no extra cluster, no network hop, and one
fewer service to babysit.

> **Preview release:** JemDB 0.1.0 is the first public binary preview. The
> public API may change before JemDB 1.0.

## Why JemDB?

Many Java applications need more search behavior than repeated collection
scans provide, but do not need the operational footprint of a separate search
cluster. JemDB occupies that space: it is a focused search component embedded
in the application process.

JemDB is intended for use cases such as:

- marketplace and catalogue search;
- vehicle, property, and accommodation search;
- location-aware search and map output;
- application-specific indexes and lookup services; and
- embedded search in web services, desktop applications, and batch tools.

JemDB is not a SQL database, document store, or standalone server. The
application remains responsible for loading and owning its source data.

## Highlights

- Primitive-first, column-oriented in-memory storage
- Compressed bitmap indexes where appropriate
- Exact numeric, boolean, and enum filters
- Inclusive numeric and date ranges
- Repeated alternatives with OR semantics
- Ascending and descending bounded sorting
- Offset and limit
- String, integer, and long primary keys
- Integer, enum, and geographic partitioning
- Latitude/longitude bounding-box search
- Reusable typed result and row cursors
- Complete-row upserts and streaming bulk ingestion
- Single-writer, many-reader operation

## When JemDB is a good fit

JemDB is a good candidate when:

- the searchable data and indexes fit comfortably in available memory;
- searches are primarily structured filters, ranges, sorting, and lookups;
- the workload is read-heavy;
- one application thread can own updates; and
- embedding search is preferable to deploying another service.

JemDB is probably not the right choice when an application requires full-text
relevance, durable transactional storage, distributed replication, a dataset
larger than available memory, or multiple simultaneous writers.

“Fits in memory” is a deployment boundary, not a small-data target. A JemDB
dataset using 1 GB of heap is not large by the standards the engine was
designed for; multi-gigabyte searchable datasets are an intended use case. The
included benchmark keeps one million fully searchable cars and their indexes
in about 189 MiB of retained heap. JemDB's design grew out of vacation-rental
search work where earlier generations operated with more than 20 GB of data in
memory. Actual capacity depends heavily on the schema, values, indexes, and JVM
heap available to the application.

## Install JemDB 0.1.0

JemDB requires Java 17 or newer. The first preview is distributed through
[GitHub Releases](https://github.com/nesalc/jemdb/releases/tag/v0.1.0) as a
protected binary library rather than as source code.

Download these two files from the release:

- `jemdb-core-0.1.0.jar` — the protected runtime library; and
- `jemdb-core-0.1.0.pom` — metadata that lets Maven resolve JemDB's runtime
  dependencies.

Keep both files in the same directory and install them once into the local
Maven repository:

```shell
mvn install:install-file \
  -Dfile=jemdb-core-0.1.0.jar \
  -DpomFile=jemdb-core-0.1.0.pom
```

Then add JemDB to the application's `pom.xml`:

```xml
<dependency>
  <groupId>com.jemdb</groupId>
  <artifactId>jemdb-core</artifactId>
  <version>0.1.0</version>
</dependency>
```

Maven obtains JemDB's third-party dependencies from Maven Central. The local
installation is needed only because GitHub Releases is a download service, not
a Maven repository.

### Smallest useful example

```java
import com.jemdb.DataException;
import com.jemdb.JemDb;
import com.jemdb.JemDbConfig;
import com.jemdb.Query;
import com.jemdb.SimpleRowData;

public final class HelloJemDb {
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

        var query = Query.builder()
                .where(available, true)
                .build();

        try (var results = db.query(query)) {
            while (results.next()) {
                System.out.println(results.get(title));
            }
        }
    }
}
```

The release also contains public Javadoc, the Elastic License 2.0 text, and
SHA-256 checksums. The complete executable examples are in the
[`examples`](examples/README.md) directory.

## Performance snapshots

The repository contains reproducible benchmark programs rather than only
headline numbers. Results depend on the dataset, schema, query shapes,
hardware, JVM, and competing system configuration.

### One million cars: JemDB versus an ArrayList

In an August 2026 Apple M2 and Java 21.0.2 run with a fixed 2 GiB heap:

- JemDB answered the principal searches in **71–294 us** median time.
- The equivalent ArrayList implementations took **4.1–10.6 ms**.
- At eight reader threads, JemDB handled **14,249 searches/second** and the
  list handled **219**.
- The list loaded faster, while JemDB retained about **9 MiB less heap** for
  this schema after the repeated make strings moved to an enum field.

See the complete methodology, results, qualifications, and reproduction steps
in the [JemDB versus ArrayList benchmark](benchmark/arraylist/README.md).

### Same Spring service: JemDB versus Elasticsearch

In a one-million-car end-to-end HTTP comparison using the same Spring
controller, response objects, dataset, and validated JSON result fingerprints:

- At one client thread, JemDB served **2,686 requests/second** and
  Elasticsearch served **460**.
- At eight client threads, JemDB served **12,431 requests/second** and
  Elasticsearch served **2,346**.
- Median latency at eight threads was **0.59 ms** for JemDB and **3.35 ms**
  for Elasticsearch.

This is not a claim that JemDB replaces Elasticsearch. Elasticsearch provides
persistence, replication, full-text search, aggregations, cluster management,
and many other capabilities that JemDB does not attempt to provide.

See the complete setup, limitations, and reproduction steps in the
[JemDB versus Elasticsearch service benchmark](benchmark/elasticsearch/README.md).

## Examples

The [`examples`](examples/README.md) directory contains three standalone,
plain-Java programs:

- [`HelloJemDb`](examples/src/main/java/com/jemdb/examples/HelloJemDb.java) —
  the smallest useful setup, insert, and query;
- [`ProductCatalogExample`](examples/src/main/java/com/jemdb/examples/ProductCatalogExample.java)
  — alternatives, numeric ranges, sorting, and primary-key lookup; and
- [`GeographicSearchExample`](examples/src/main/java/com/jemdb/examples/GeographicSearchExample.java)
  — geographic partitioning and map-bounds search.

Each example has its own `main` method and requires no application framework.

## Web demo

The [`demo/car-rental`](demo/car-rental/README.md) directory contains a small
Spring Boot application backed by JemDB. It loads a recognizable sample car
inventory and exposes structured searches, ranges, repeated alternatives, and
primary-key lookup through a JSON HTTP API.

The demo is a standalone Maven project, so it can be built and run without
turning this repository into a multi-module build.

## Repository contents

```text
examples/              Three small, executable JemDB examples
demo/
└── car-rental/        Spring Boot car-rental search demo
benchmark/
├── data/              Shared deterministic benchmark data
├── arraylist/         JemDB and Java collection comparison
└── elasticsearch/     End-to-end Spring service comparison with Elasticsearch
```

The example, demo, and benchmark source is public so it can be inspected and
reproduced. The JemDB engine implementation is not included in this repository.

## Distribution

JemDB is distributed as a compiled, protected Java library. Preview JARs,
public Javadoc, checksums, and installation instructions are published through
GitHub Releases.

- The project domain is [jemdb.com](https://jemdb.com).
- Public Java packages use the `com.jemdb` namespace.
- Java 17 or newer is required.
- The implementation source is not part of the public distribution.
- The first artifact coordinates are `com.jemdb:jemdb-core:0.1.0`.

## Licensing

JemDB, including its binary releases, examples, demos, and benchmarks, is
available under the [Elastic License 2.0](LICENSE.txt).

You may use JemDB freely in internal, self-managed, commercial, and SaaS
applications. The main restriction is that you may not offer substantial JemDB
functionality to others as a hosted or managed JemDB service.

In practical terms: use it in your application, build something useful, and
have fun. Just do not repackage JemDB itself as your own hosted JemDB product.

JemDB is not an open-source engine: its implementation source is private.

## Project status

JemDB is currently a preview project. Expect changes to configuration,
diagnostics, and packaging before the first stable release.
Preview releases will not initially carry a stable API compatibility guarantee.
