# JemDB examples

These examples are deliberately small and use plain Java. Each class has its
own `main` method, creates an in-memory database, loads a few rows, and runs a
search.

- [`HelloJemDb`](src/main/java/com/jemdb/examples/HelloJemDb.java) is the
  smallest useful example.
- [`ProductCatalogExample`](src/main/java/com/jemdb/examples/ProductCatalogExample.java)
  combines alternatives, numeric ranges, sorting, and a primary-key lookup.
- [`GeographicSearchExample`](src/main/java/com/jemdb/examples/GeographicSearchExample.java)
  demonstrates geographic partitioning and a map-bounds search.

## Run an example

JemDB requires Java 17 or newer. Once the JemDB preview JAR has been installed,
run an example from this directory:

```shell
mvn -q compile exec:java -Dexec.mainClass=com.jemdb.examples.HelloJemDb
```

Replace the class name with `ProductCatalogExample` or
`GeographicSearchExample` to run either of the larger examples.
