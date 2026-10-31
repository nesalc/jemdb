# JemDB car-rental search demo

A small Spring Boot application that uses `jemdb-core` as an external Maven
dependency. It loads 24 recognizable rental cars into memory and exposes fast
searches over HTTP.

The sample inventory spans Denmark, France, Germany, Italy, Spain, the United
Kingdom, and the United States. It includes familiar vehicles from Audi, BMW,
Ford, Honda, Mercedes-Benz, Peugeot, Tesla, Toyota, Volkswagen, Volvo, and
other manufacturers.

## Build and run

JemDB requires Java 17 or newer. Once the current JemDB preview JAR has been
installed in your local Maven repository, start the demo from the JemDB
repository root:

```bash
cd demo/car-rental
mvn spring-boot:run
```

## Try it

Find affordable SUVs in the US with room for at least five people and four bags:

```bash
curl 'http://localhost:8080/api/cars?pickupCountry=US&category=SUV&seats=5..&bags=4..&dailyPrice=..100'
```

Find electric Teslas or Fords in the US. Repeated `make` parameters use OR logic:

```bash
curl 'http://localhost:8080/api/cars?pickupCountry=US&make=Tesla&make=Ford&electric=true'
```

Retrieve one car directly by its primary key:

```bash
curl 'http://localhost:8080/api/cars/DE-BER-001'
```

## Filters

| Parameter | Examples |
| --- | --- |
| `pickupCountry` | `US`, `DE`, `DK`, `ES`, `FR`, `IT`, `UK` |
| `make` | `Toyota`, `Volkswagen`, `Ford`, `Tesla`, `BMW` |
| `category` | `Economy`, `Compact`, `SUV`, `Premium`, `Convertible`, `Van` |
| `seats` | `5`, `5..`, `4..7`, `..5` |
| `bags` | `3`, `3..`, `2..4`, `..2` |
| `automatic` | `true`, `false` |
| `electric` | `true`, `false` |
| `dailyPrice` | `60`, `60..`, `50..100`, `..80` (EUR) |
| `limit` | Defaults to `50`, maximum `1000` |

Ranges are inclusive. Repeated values for the same non-boolean field use OR
logic, while filters on different fields use AND logic. Boolean filters accept
one value because `true OR false` would be equivalent to not filtering that
field.

Ranges and repeated exact values cannot currently be mixed for the same field.
For example, `dailyPrice=40..60&dailyPrice=100` returns HTTP 400.

The HTTP layer converts readable country, make, and category names into
primitive integer codes. JemDB searches those codes and the numeric ranges
using indexes, then the cursor is materialized into JSON records by the demo
application.
