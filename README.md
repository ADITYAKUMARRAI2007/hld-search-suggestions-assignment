# Production Search Typeahead System

Java/Spring Boot implementation of the search typeahead HLD assignment. The project is built to show the system-design answer in code: prefix suggestions, search submission, query-count updates, distributed cache routing with consistent hashing, trending searches, batch writes, metrics, UI, screenshots, and performance evidence.

## Quick Start

```bash
mvn -Dmaven.repo.local=.m2/repository test
mvn -Dmaven.repo.local=.m2/repository spring-boot:run
```

Open:

- UI: `http://127.0.0.1:8080`
- Swagger/OpenAPI: `http://127.0.0.1:8080/docs`
- Health: `http://127.0.0.1:8080/actuator/health`

The default profile starts with an in-memory smoke dataset so the evaluator can test the APIs immediately. The production/Docker profile is configured for PostgreSQL, OpenSearch, Kafka, and Redis shards.

```bash
docker compose up --build
```

## Architecture

```text
Browser UI
  -> Spring Boot API
      -> Suggestion Service
          -> ConsistentHashRing
          -> Prefix Cache Shards
          -> OpenSearch/In-memory Suggestion Index
      -> Search API
          -> Kafka/In-memory Event Publisher
      -> Batch Aggregator
          -> query-count upsert
          -> trend bucket update
          -> suggestion weight refresh
          -> prefix cache invalidation
      -> Metrics
```

Production choices:

- **Java 21 + Spring Boot**: production backend framework with validation, metrics, static UI, and OpenAPI.
- **PostgreSQL**: durable source of truth for query counts and trend buckets.
- **OpenSearch**: low-latency prefix autocomplete read model using weighted completion fields.
- **Redis shards**: low-latency prefix-result cache.
- **Java consistent hashing**: deterministic routing of prefix cache keys to cache nodes with virtual nodes.
- **Kafka**: durable event buffer so search requests do not synchronously write every count update.

More detail: [docs/architecture.md](docs/architecture.md)

## Dataset

Primary dataset: **AmazonQAC**  
Source: <https://huggingface.co/datasets/amazon/AmazonQAC>  
License: CDLA-Permissive-2.0

Why it fits:

- Real query-autocomplete dataset.
- Contains `final_search_term`, `popularity`, `search_time`, and typed `prefixes`.
- Dataset page lists Parquet format and a 100M-1B row scale.
- Train split is far above the required 100,000 queries.

The Java importer accepts AmazonQAC-derived CSV or JSONL:

```bash
mvn -Dmaven.repo.local=.m2/repository spring-boot:run \
  -Dspring-boot.run.arguments="--typeahead.dataset.import-path=/path/to/amazonqac_100k.csv --typeahead.dataset.import-limit=100000"
```

Expected CSV fields:

```text
final_search_term,popularity,search_time,prefixes
```

A tiny smoke fixture is included at [data/sample-amazonqac-smoke.csv](data/sample-amazonqac-smoke.csv). It is only for local smoke testing; the grading dataset source remains AmazonQAC.

## APIs

Required aliases and versioned endpoints are both implemented.

```bash
curl 'http://127.0.0.1:8080/suggest?q=iph&rank=count'
curl 'http://127.0.0.1:8080/suggest?q=iph&rank=hybrid&debug=true'
curl -X POST 'http://127.0.0.1:8080/search' \
  -H 'Content-Type: application/json' \
  -d '{"query":"iphone 15"}'
curl 'http://127.0.0.1:8080/trending?window=1h'
curl 'http://127.0.0.1:8080/cache/debug?prefix=iph'
curl 'http://127.0.0.1:8080/metrics'
```

API docs: [docs/api.md](docs/api.md)

## Trending Search Scoring

The basic mode is available with `rank=count` and sorts only by historical count.

The extra-credit/full-marks mode is `rank=hybrid`:

```text
score =
  log10(historical_count + 1) * 100000
  + log10(recent_count_1h + 1) * 60000
  + log10(recent_count_24h + 1) * 25000
```

Recent searches are tracked through batch-aggregated 5-minute buckets. The API supports `1h`, `24h`, and `7d` trending windows. Old spikes stop dominating because old buckets fall out of the selected window, cache TTLs are short for hybrid ranking, and updated query prefixes are invalidated after batch flushes.

## Batch Writes

`POST /search` accepts the search and queues an event. The batch worker aggregates repeated queries and flushes every 2 seconds or 500 events.

Example from the live smoke benchmark:

```text
Search events received: 540
DB write operations: 7
Writes saved: 533
Write reduction: 98.70%
```

Failure trade-off:

- Kafka protects accepted events in the production profile.
- Worker crashes are handled by replay.
- Redis/OpenSearch can be briefly stale.
- PostgreSQL remains the source of truth.

More detail: [docs/tradeoffs.md](docs/tradeoffs.md)

## Performance Evidence

Smoke-profile benchmark command:

```bash
./scripts/benchmark.sh
```

Measured on the local smoke profile:

```text
Suggest requests: 203
Cache hit rate: 94.58%
Average suggest latency: 0.16 ms
P95 suggest latency: 0.34 ms
Search events: 540
DB writes: 7
Writes saved: 533
Write reduction: 98.70%
```

Full report: [docs/performance-report.md](docs/performance-report.md)

## Screenshots And Demo

Screenshots:

- [Trending-aware suggestions](docs/screenshots/search-ui-suggestions.png)
- [Overall historical ranking](docs/screenshots/search-ui-overall-mode.png)

Demo script:

```bash
./scripts/demo-flow.sh
```

The demo proves the required ranking difference: `rank=count` keeps the historically popular query first, while repeated searches lift the same-prefix query in `rank=hybrid`.

## Rubric Checklist

| Component | Marks | Status |
|---|---:|---|
| Basic implementation | 60 | Implemented: dataset import path, UI, suggestions API, search API, count updates, consistent-hash prefix cache. |
| Trending searches | 20 | Implemented: windowed buckets, `/trending`, hybrid ranking, cache invalidation, visible count-vs-hybrid demo. |
| Batch writes | 20 | Implemented: event queue abstraction, Kafka profile, batch aggregation, write-reduction metrics, failure trade-offs. |

## Submission Notes

- GitHub push still requires repository remote/access approval.
- AmazonQAC full train data is not committed because the dataset is large.
- The included screenshots and smoke benchmark were generated from the local runnable profile.
