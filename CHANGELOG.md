# JemDB release notes

## 0.1.0 — First preview

JemDB 0.1.0 is the first public binary preview of the embedded, in-memory
search engine that grew from years of structured-search work in the vacation
rental domain.

It is for Java applications that need fast filters, ranges, bounded sorting,
primary-key lookup, and geographic search without adding a separate search
server and network hop.

### Included in this preview

- Primitive-first, column-oriented in-memory storage
- Compressed bitmap indexes where appropriate
- Integer, long, double, boolean, string, enum, date, and location fields
- Exact filters, repeated alternatives, and inclusive ranges
- Ascending and descending bounded sorting, offset, and limit
- String, integer, and long primary keys
- Integer, enum, and geographic partitioning
- Latitude/longitude bounds and calculated point-distance search
- Reusable typed row and result cursors
- Complete-row upserts and streaming bulk ingestion
- A narrow custom integer-field extension API
- Single-writer, many-reader operation

The repository includes plain-Java examples, a Spring Boot car-rental demo,
one-million-row benchmarks against an ArrayList, and an end-to-end Spring
service comparison with Elasticsearch. The benchmark documentation includes
the setup, raw methodology, qualifications, and reproduction steps—not just
the nice numbers.

### Deliberate first-version boundaries

JemDB 0.1.0 is not a SQL database, durable data store, full-text relevance
engine, or distributed cluster. It assumes that the searchable data fits in
memory, the workload is read-heavy, and one application thread owns updates.

Rows are currently replaced as complete units. Missing values use
application-chosen defaults. The public API may change before JemDB 1.0.

### Distribution and license

JemDB requires Java 17 or newer. Version 0.1.0 is distributed through GitHub
Releases as a protected binary JAR with a Maven POM, public Javadoc, license,
and SHA-256 checksums.

JemDB is free to use under the Elastic License 2.0 in internal, self-managed,
commercial, and SaaS applications. It may not be offered as a hosted or
managed JemDB service. The engine implementation source remains private.

Installation instructions are in the project [README](README.md).
