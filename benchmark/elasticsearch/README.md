# JemDB versus Elasticsearch: same service, one fewer server

We know you came for the numbers, so here is the short version first.

In the August 2026 one-million-car Spring Boot comparison:

- With one client thread, JemDB served **2,686 requests/second** and
  Elasticsearch served **460**.
- With eight client threads, JemDB reached **12,431 requests/second** and
  Elasticsearch reached **2,346**.
- Median latency at eight threads was **0.59 ms** for JemDB and **3.35 ms**
  for Elasticsearch.
- Both services returned the same complete, validated JSON result
  fingerprints.
- Preparing all one million cars took a median **1.28 seconds** for JemDB and
  **13.08 seconds** for the verified, force-merged Elasticsearch index.

That is a very nice result for JemDB. It is not a claim that every JemDB query
beats every Elasticsearch setup. Let us look at what was compared, why the
simpler architecture matters, and where the numbers need an asterisk.

This benchmark compares two ways to build the same Spring Boot car-search
service:

1. Put JemDB inside the Spring process.
2. Let Spring call a separate Elasticsearch server.

Both are perfectly respectable choices. Elasticsearch is a large, mature
search platform with many capabilities JemDB does not try to provide. JemDB is
a focused in-memory search component with a rather attractive deployment
diagram: start the Java application, and you are done.

This is not an attempt to declare the fastest search engine in the world.
Better comparisons can always be made, and JemDB will be slower for some
datasets and query shapes. The interesting question here is simpler:

> If embedded search is enough for an application, how much speed and
> operational simplicity can we get by skipping the second server?

## The two setups

### Embedded JemDB

- One Spring Boot process
- JemDB in the same JVM
- One 2 GiB Java heap
- No search-service network hop
- One million cars loaded from a compressed CSV in about 1.3 seconds

### Elasticsearch

- The same Spring Boot application
- The official pooled Elasticsearch Java client
- A second Elasticsearch process in Docker
- A 2 GiB Spring heap
- A 4 GiB Elasticsearch container with a 2 GiB Elasticsearch heap
- One primary shard and no replica

The Elasticsearch setup deliberately gets more total memory and another
process. That is fair: the extra server is part of the architecture being
tested, and it should have enough resources to do its job.

## What stays identical

The Spring controller, endpoints, response objects and external benchmark
client are the same for both profiles. Both search the same deterministic one
million-car catalogue. The checked-in gzip used for the August 2026 results has
SHA-256
`ee3aeccdb422c2507f1bb2f6b4b85b94ff767d5131db33d833bcf2c629653208`.

The balanced request mix contains:

1. Toyota filtering, price ordering and 50 complete cars.
2. An exact electric-SUV count with no returned cars.
3. Electric-SUV filtering, mileage ordering and 50 complete cars.
4. Volkswagen filtering, price ordering and 100 complete cars.

Every non-count response returns all eleven car fields. The external client
reads and validates the complete JSON body, including match count, returned-row
count and checksum. Matching fingerprints are required before throughput is
compared.

Elasticsearch tracks exact total hits. Its request cache is disabled per
request, while the ordinary query cache remains at its production default.
The read-only cars index is force-merged to one segment after loading.

JemDB does not cache query results. Every request executes its filters, count
and sorting again, using the already loaded in-memory data and indexes; it does
not reuse an earlier result set.

In JemDB, the repeated make name uses an enum field: each row stores one
unsigned-byte dictionary ID, queries use names such as `Toyota`, and results
still return the original string. That enum is also the JemDB partition key.
Elasticsearch uses its indexed make ID for the equivalent exact filter.

## Getting one million cars ready

Loading is part of the experience too, especially for an in-memory service
that should be able to restart without turning coffee into a dependency.

| Preparation | Median time | What is included |
|---|---:|---|
| JemDB | 1.28 seconds | Read compressed CSV and build the complete in-memory searchable dataset |
| Elasticsearch bulk phase | 11.69 seconds | Read the same compressed CSV, create JSON and send all bulk indexing requests |
| Elasticsearch ready | 13.08 seconds | Bulk phase, refresh, force-merge, restore settings and verify one million documents |

These are medians from three fresh JemDB processes and three Elasticsearch
index recreations on the same quiet machine. The individual JemDB times were
1.293, 1.267 and 1.276 seconds. Elasticsearch's bulk times were 12.03, 11.60
and 11.69 seconds; the corresponding complete ready times were 13.53, 12.97
and 13.08 seconds.

JemDB was therefore about **10.3x faster** at preparing this particular
dataset. That is useful when an application starts, replaces its dataset or
wants a small deployment with a quick recovery path.

There is an important difference: JemDB rebuilds its in-memory state when the
application starts. Elasticsearch stores its index on disk, so this initial
indexing step normally happens once; later Spring service starts simply
connect to the existing index. The Elasticsearch measurement also excludes
container startup. One unreported first-use indexing run warmed Elasticsearch
after startup before the three recorded recreations. This table compares
repeatable data preparation after server initialization, not recurring service
startup with an already prepared persistent index or a first-ever Docker
startup.

## A recent quiet-machine result

This run used:

- Fanless Apple M2 MacBook Air with 16 GiB of memory
- macOS 26.1
- Java 21.0.2
- Spring Boot 4.1.0
- Elasticsearch 9.4.2 under Docker Desktop
- One million cars
- Five seconds of warm-up and ten seconds of measurement per thread count
- 1, 2, 4 and 8 external client threads

The backends were measured separately: Elasticsearch and Docker were fully
stopped for JemDB, and the JemDB service was stopped before Elasticsearch was
measured. Noisy background applications were closed for the measurements.

The JemDB table is one complete validated sweep after a fresh process load.
Elasticsearch received one complete disposable sweep after its container
restart, followed by a 105-second cooling interval and the recorded sweep
below. The disposable sweep produced 424, 1,013, 2,024 and 1,588
requests/second. Its eight-client result degraded under sustained load; it had
already been designated as disposable and was not replaced merely because it
was slower.

### End-to-end HTTP throughput

| Client threads | JemDB req/s | JemDB scaling | Elasticsearch req/s | Elasticsearch scaling |
|---:|---:|---:|---:|---:|
| 1 | 2,686 | 1.00x | 460 | 1.00x |
| 2 | 6,185 | 2.30x | 939 | 2.04x |
| 4 | 9,984 | 3.72x | 1,987 | 4.32x |
| 8 | 12,431 | 4.63x | 2,346 | 5.10x |

### Client-observed median latency

| Client threads | JemDB | Elasticsearch |
|---:|---:|---:|
| 1 | 0.38 ms | 2.15 ms |
| 2 | 0.34 ms | 2.10 ms |
| 4 | 0.40 ms | 1.98 ms |
| 8 | 0.59 ms | 3.35 ms |

At eight threads, JemDB delivered 93.99 MiB/s of complete JSON responses with a
1.11 ms p95 latency. Elasticsearch delivered 17.74 MiB/s with a 5.75 ms p95.

On this machine and workload, JemDB was about 5.8x faster at one thread and
5.3x faster at eight threads. Those numbers are interesting, but the simpler
setup is at least as important:

- no second service to deploy;
- no search cluster to configure or monitor;
- no application-to-search HTTP traffic;
- no duplicated client/server serialization;
- and a million searchable rows ready in roughly 1.3 seconds.

That can be a pleasant trade when the application fits in memory and JemDB
supports the searches it needs.

## This is not “JemDB replaces Elasticsearch”

Elasticsearch offers distributed storage, replication, persistence, full-text
search, analyzers, aggregations, cluster management and a very broad ecosystem.
If an application needs those capabilities, a focused embedded library is not
a substitute.

JemDB may also lose when:

- the dataset or indexes do not fit comfortably in memory;
- the search shape does not suit its indexes;
- queries require capabilities it does not provide;
- persistence and distributed failover are requirements;
- a mature Elasticsearch cluster already exists and is well tuned;
- or the dataset is small enough that none of this matters.

This benchmark covers exact filters, numeric ranges, counts, bounded sorting
and complete result objects. It does not cover text relevance, aggregations,
continuous updates, multiple Elasticsearch shards or a multi-node cluster.

The benchmark also runs Elasticsearch through Docker Desktop on macOS. A
serious published comparison should be repeated on dedicated Linux hardware
where both deployments run natively.

## Build the benchmark

First install JemDB 0.1.0 in the local Maven repository by following the
[main installation guide](../../README.md). It uses the protected JAR and POM
from the GitHub Release; the private engine source is not required.

Build the shared Spring service:

~~~shell
cd /path/to/jemdb/benchmark/elasticsearch
mvn clean package
~~~

The benchmark expects the shared dataset at
../data/cars-1m.csv.gz.

## Prepare Elasticsearch

Start the official single-node container:

~~~shell
docker compose up -d
docker compose ps
~~~

Load the million cars:

~~~shell
mvn -q compile exec:exec \
  -Dexec.executable=java \
  '-Dexec.args=-cp %classpath com.jemdb.benchmark.elasticsearch.ElasticsearchIndexLoader'
~~~

The loader recreates the benchmark index named **cars**, bulk-loads it with
periodic refresh disabled, refreshes once, force-merges it to one segment and
verifies the final document count. It prints both the bulk-indexing time and
the complete time until the search index is ready.

Do not point the loader at an Elasticsearch installation containing a cars
index you care about.

## Run the JemDB service

Stop Elasticsearch so the embedded setup does not compete with a server it
does not need:

~~~shell
docker compose stop elasticsearch
~~~

Start Spring with the JemDB profile:

~~~shell
java -Xms2g -Xmx2g \
  -jar target/jemdb-service-benchmark-0.1.0.jar \
  --spring.profiles.active=jemdb \
  --server.port=9081
~~~

After /api/status reports one million rows, run the external client from a
second terminal:

~~~shell
mvn -q compile exec:exec \
  -Dexec.executable=java \
  '-Dexec.args=-cp %classpath com.jemdb.benchmark.elasticsearch.HttpConcurrentBenchmark http://localhost:9081'
~~~

Stop the Spring process before switching backends.

## Run the Elasticsearch service

Restart the existing container and wait for green health:

~~~shell
docker compose start elasticsearch
curl 'http://localhost:9200/_cluster/health?wait_for_status=green&timeout=60s'
~~~

Start the same Spring JAR with the Elasticsearch profile:

~~~shell
java -Xms2g -Xmx2g \
  -jar target/jemdb-service-benchmark-0.1.0.jar \
  --spring.profiles.active=elasticsearch \
  --server.port=9081
~~~

Run the same external client command. After a fresh Elasticsearch restart,
perform at least one complete disposable run before recording results. Repeat
the measured run and report the variation rather than selecting a convenient
outlier.

## Configure duration and concurrency

The client defaults to five seconds of warm-up and ten seconds of measurement
at 1, 2, 4 and 8 threads:

~~~shell
mvn -q compile exec:exec \
  -Dexec.executable=java \
  '-Dexec.args=-Dbenchmark.concurrentWarmupMillis=5000 -Dbenchmark.concurrentMeasureMillis=10000 -Dbenchmark.concurrentThreads=1,2,4,8 -cp %classpath com.jemdb.benchmark.elasticsearch.HttpConcurrentBenchmark http://localhost:9081'
~~~

Individual workloads can be selected with the **benchmark.workloads** property.
For example:

~~~shell
mvn -q -Dbenchmark.workloads=ELECTRIC_MILEAGE \
  -Dbenchmark.concurrentWarmupMillis=2000 \
  -Dbenchmark.concurrentMeasureMillis=5000 \
  exec:java \
  -Dexec.mainClass=com.jemdb.benchmark.elasticsearch.HttpConcurrentBenchmark
~~~

The separate /api/search/volkswagen/export endpoint returns all 6,780 matching
rows and remains available as an output-generation stress test. It is not part
of the normal balanced mix.

## Keep it honest

Run the services separately. Close noisy applications. Watch memory pressure
and swap. Repeat each measurement. Keep matching result fingerprints, and
publish the environment alongside the numbers.

There is nothing wrong with a benchmark showing that another design wins.
The useful result is understanding when a simpler embedded search service is
enough—and when bringing another server is worth it.

When finished, stop the container while keeping the index:

~~~shell
docker compose stop elasticsearch
~~~

Use docker compose down --volumes only when the benchmark data should be
deleted.
