# JemDB versus a Java ArrayList

We know why you are here, so let us start with the fun part.

With one million cars, the August 2026 run looked like this:

- JemDB answered the four searches in **71–294 us** median time.
- The ArrayList implementations took **4.1–10.6 ms**.
- At eight reader threads, JemDB handled **14,249 searches/second** while the
  list handled **219**.
- The list did win the loading race: **671 ms** versus **1,272 ms** for JemDB,
  which also built its searchable indexes.
- JemDB retained about **189 MiB** for the dataset and indexes, around **9 MiB
  less** than the object list.

Those are encouraging numbers, but they are not a universal law of Java. Now
for what we actually measured and why the comparison is useful.

This is a friendly benchmark, not a cage fight.

It asks a practical question:

> If a Java application has one million cars to search, what do we gain by
> using JemDB instead of writing loops over a list ourselves?

Sometimes the list is the right answer. For a small dataset and two fixed
queries, a few carefully written loops can be wonderfully fast. They can also
grow into a home-made search engine once filtering, ranges, sorting, paging,
counts, primary keys, updates and several output formats arrive.

JemDB is meant for that second situation. It keeps the data inside the Java
process, provides reusable search behavior, and does not require a separate
search server.

## What is compared

Both programs load the same deterministic file containing 1,000,000 used cars.
It includes 44 familiar European, US and Asian makes, multiple models, years,
prices, mileages, fuel types, body types, transmission flags and cities.
The August 2026 file has SHA-256
`ee3aeccdb422c2507f1bb2f6b4b85b94ff767d5131db33d833bcf2c629653208`;
regenerating it with the documented command produced a byte-identical file.

- **JemDbBenchmark** stores the cars in JemDB and uses its public query API.
- **ListBenchmark** stores immutable Car objects in an ArrayList and uses
  direct Java loops plus bounded priority queues.

The concurrent versions share the same loaded data between reader threads:

- **JemDbConcurrentBenchmark**
- **ListConcurrentBenchmark**

Every workload reports match counts, returned-row counts and checksums. Those
fingerprints must agree before timing numbers are compared.

Neither implementation caches query results. Every warm-up and measured
operation executes its filters, count and sorting again. JemDB reuses the
loaded in-memory data and indexes, while the list implementation scans its
loaded objects; neither side reuses an earlier result set.

The search mix contains:

1. Toyota cars filtered and sorted by price, returning 50 rows.
2. Electric SUVs as an exact count with no returned rows.
3. Electric SUVs sorted by mileage, returning 50 rows.
4. Volkswagen cars sorted by price, returning 100 rows.
5. Twenty-five primary-key lookups in the single-thread benchmark.

## A recent result

This example was recorded on a fanless Apple M2 MacBook Air with 16 GiB of
memory, macOS 26.1, Java 21.0.2 and a fixed 2 GiB heap for each process. It is
a result from one machine, not a promise about yours.

### Loading

| Implementation | One million cars |
|---|---:|
| JemDB | 1,272 ms |
| ArrayList of Car | 671 ms |

The list loads faster, as we should expect from the simpler structure. JemDB
also builds its searchable representation and becomes ready in about 1.3
seconds.

### Retained heap after loading

| Implementation | Loaded heap | Dataset increase | Approx. bytes/car |
|---|---:|---:|---:|
| JemDB | 190.17 MiB | 188.98 MiB | 198.2 |
| ArrayList of Car | 199.19 MiB | 198.02 MiB | 207.6 |

JemDB retains about **9.0 MiB less**, or roughly **4.6% less**, than the object
list for this schema. Repeated make strings use JemDB's enum field: one compact
unsigned-byte value ID per row plus a small dictionary. The cumulative range
indexes intentionally spend some of that saving to make range searches faster.
The make field remains queryable by its friendly string value.

This is not a general claim that JemDB always compresses Java objects. The
result depends heavily on repeated strings, schema choices and indexes. Here,
the complete million-car dataset and its searchable representation retain
about 189 MiB.

These are medians from three fresh JVM runs with identical 2 GiB heaps. Each
program measures its empty baseline, loads the cars, performs five explicit
garbage collections, and uses the lowest resulting live-heap measurement.
“Dataset increase” is loaded heap minus baseline heap.

The number is an approximation of retained Java heap—not process RSS,
committed heap or peak allocation during CSV parsing. Different fields,
strings, indexes, partitions and JVM implementations will change it.

### Single-thread search latency

| Workload | JemDB median | ArrayList median | Difference |
|---|---:|---:|---:|
| Toyota sorted by price | 108.42 us | 10,134.88 us | JemDB 93.5x |
| Electric SUVs count | 268.63 us | 4,092.96 us | JemDB 15.2x |
| Electric SUVs by mileage | 294.04 us | 6,657.25 us | JemDB 22.6x |
| Volkswagen top 100 | 71.25 us | 10,617.92 us | JemDB 149.0x |
| 25 primary-key lookups | 1.54 us | 77,835.63 us | JemDB 50,543x |

Each value is the median of the three fresh-JVM run medians. Within each run,
the program performs 300 unmeasured warm-up rounds followed by 1,000 measured
rounds, and all result fingerprints must match. Warm-up lets the JVM optimize
the hot code before measurement; it is not included in the reported latency.

The primary-key comparison deserves a large asterisk. The list baseline uses a
linear search because that is the structure being compared. A real application
could add a HashMap and make those lookups much faster. It would then own and
maintain another index—which is exactly the kind of work JemDB is intended to
take care of.

### Concurrent balanced search

| Reader threads | JemDB req/s | JemDB scaling | ArrayList req/s | ArrayList scaling |
|---:|---:|---:|---:|---:|
| 1 | 4,556 | 1.00x | 121 | 1.00x |
| 2 | 7,449 | 1.63x | 197 | 1.63x |
| 4 | 10,851 | 2.38x | 259 | 2.14x |
| 8 | 14,249 | 3.13x | 219 | 1.81x |

The concurrent values are medians from three complete quiet-machine sweeps.
Runs with detected competing background workloads were discarded and repeated
in full. Every retained sweep produced matching result fingerprints.

This is where the in-memory indexes become especially useful. JemDB narrows the
working set before sorting or producing output, while the list repeatedly walks
the million objects.

## What this benchmark does and does not say

It shows that JemDB fits this dataset and these query shapes very well. It also
shows that an embedded search component can provide high read throughput
without a network hop or another server.

It does **not** show that JemDB beats every Java data structure:

- A smaller dataset may be faster with ordinary collections.
- A purpose-built structure for one known query may beat a general query API.
- Different distributions, filters and sort orders can change the result.
- A list can be supplemented with maps, trees and other indexes.
- JVM, CPU, memory, operating-system load and schema choices all matter.

Adding those extra structures is perfectly valid. At some point, though, the
application team is building and maintaining its own search service. JemDB is
an attempt to make that part pleasantly boring.

## The JemDB schema used here

Make uses an enum field, storing one unsigned-byte dictionary ID per row while
queries still use names such as `Toyota` and `Volkswagen`. Fuel type, body type
and year use cumulative greater-or-equal bitmap indexes; an exact bucket is the
difference between two adjacent indexes. Year uses one-year buckets. Price and
mileage use linear primitive storage because earlier selective filters make
scanning the remaining candidates cheaper for these workloads.

Filter order follows schema order, so selective fields are declared before
broader numeric ranges. This is part of the benchmark configuration and should
not be silently changed when reproducing the result.

The schema is workload-specific, not a universal recommendation. JemDB permits
at most 256 configured buckets for a cumulative range field so loading and
memory costs remain bounded.

## Prerequisite

Install JemDB 0.1.0 in the local Maven repository by following the
[main installation guide](../../README.md). It uses the protected JAR and POM
from the GitHub Release; the private engine source is not required.

Then enter this benchmark directory:

~~~shell
cd /path/to/jemdb/benchmark/arraylist
~~~

The repository already contains ../data/cars-1m.csv.gz.

## Run the single-thread benchmark

Run each implementation in a separate JVM:

~~~shell
mvn -q compile exec:exec \
  -Dexec.executable=java \
  '-Dexec.args=-Xms2g -Xmx2g -cp %classpath com.jemdb.benchmark.arraylist.JemDbBenchmark'
~~~

~~~shell
mvn -q compile exec:exec \
  -Dexec.executable=java \
  '-Dexec.args=-Xms2g -Xmx2g -cp %classpath com.jemdb.benchmark.arraylist.ListBenchmark'
~~~

Each program performs 300 warm-up rounds and 1,000 measured rounds. Override them
inside the child JVM arguments:

~~~shell
'-Dexec.args=-Xms2g -Xmx2g -Dbenchmark.warmup=500 -Dbenchmark.rounds=2000 -cp %classpath com.jemdb.benchmark.arraylist.JemDbBenchmark'
~~~

Both applications print their retained-heap measurement before running the
queries. The measurement is deliberately performed before warm-up so temporary
CSV parsing objects do not become part of the reported dataset size.

## Run the concurrent benchmark

~~~shell
mvn -q compile exec:exec \
  -Dexec.executable=java \
  '-Dexec.args=-Xms2g -Xmx2g -cp %classpath com.jemdb.benchmark.arraylist.JemDbConcurrentBenchmark'
~~~

~~~shell
mvn -q compile exec:exec \
  -Dexec.executable=java \
  '-Dexec.args=-Xms2g -Xmx2g -cp %classpath com.jemdb.benchmark.arraylist.ListConcurrentBenchmark'
~~~

Defaults are two seconds of warm-up and five seconds of measurement at 1, 2, 4
and 8 reader threads. They can be changed with:

~~~shell
'-Dexec.args=-Xms2g -Xmx2g -Dbenchmark.concurrentWarmupMillis=5000 -Dbenchmark.concurrentMeasureMillis=10000 -Dbenchmark.concurrentThreads=1,2,4,8 -cp %classpath com.jemdb.benchmark.arraylist.JemDbConcurrentBenchmark'
~~~

To isolate individual zero-based workloads, use
**-Dbenchmark.workloadIndexes=2**. Index 2, for example, runs only the
electric-SUV mileage sort.

## Generate another dataset

The checked-in file is deterministic and can be regenerated:

~~~shell
mvn -q compile exec:java \
  -Dexec.mainClass=com.jemdb.benchmark.arraylist.CarCsvGenerator
~~~

Generate a different number of rows by supplying a path and count:

~~~shell
mvn -q compile exec:java \
  -Dexec.mainClass=com.jemdb.benchmark.arraylist.CarCsvGenerator \
  -Dexec.args="../data/cars-2m.csv.gz 2000000"
~~~

## Reproducing responsibly

Run the programs separately, keep the machine quiet, repeat the measurements,
and report the environment. A benchmark is most useful when it helps us learn
where a design works—not when it is polished until it wins a headline.
