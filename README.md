# Production Search Typeahead System

Java/Spring Boot implementation of the search typeahead HLD assignment. The project is built to show the system-design answer in code: prefix suggestions, search submission, query-count updates, distributed cache routing with consistent hashing, trending searches, batch writes, metrics, UI, screenshots, and performance evidence.

## Quick Start

```bash
mvn -Dmaven.repo.local=.m2/repository test
mvn -Dmaven.repo.local=.m2/repository spring-boot:run
```

Open after the app starts:

- UI: `http://127.0.0.1:8080`
- Swagger/OpenAPI: `http://127.0.0.1:8080/docs`
- Health: `http://127.0.0.1:8080/actuator/health`

These localhost URLs are only for evaluator/local execution after cloning the public GitHub repository. The submitted HLD architecture is the production-style service layout described below and in the project report.

The default profile starts with an in-memory smoke dataset so the evaluator can test the APIs immediately. The production/Docker profile is configured for PostgreSQL, OpenSearch, Kafka, and Redis shards.

```bash
docker compose up --build
```

To load a real AmazonQAC export into the Docker stack, put the file under `data/` and pass the import path:

```bash
TYPEAHEAD_DATASET_IMPORT_PATH=/data/amazonqac_100k.csv \
TYPEAHEAD_DATASET_IMPORT_LIMIT=100000 \
docker compose up --build
```

## Architecture

```text
Users / Browser
  -> CDN / Load Balancer / API Gateway
  -> Spring Boot API Service Replicas

GET /suggest
  -> Suggestion Service
  -> Consistent Hash Ring
  -> Redis Prefix Cache Shards
  -> OpenSearch Suggestion Index on cache miss

POST /search
  -> Kafka search-events topic
  -> Batch Writer Workers
  -> PostgreSQL counts + trend buckets
  -> OpenSearch weight refresh
  -> Redis affected-prefix invalidation

Observability
  -> Spring Boot Actuator + Micrometer metrics
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
- The published dataset size is far above the required 100,000 queries.

The Java importer accepts both the assignment format (`query,count`) and AmazonQAC-derived CSV/JSONL (`final_search_term,popularity,search_time,prefixes`). This project does not train a model; it imports real queries, indexes them for prefix lookup, and ranks them with historical and recent counts.

```bash
mvn -Dmaven.repo.local=.m2/repository spring-boot:run \
  -Dspring-boot.run.arguments="--typeahead.dataset.import-path=/path/to/amazonqac_100k.csv --typeahead.dataset.import-limit=100000"
```

Accepted CSV fields:

```text
query,count
```

or:

```text
final_search_term,popularity,search_time,prefixes
```

A tiny smoke fixture is included at [data/sample-amazonqac-smoke.csv](data/sample-amazonqac-smoke.csv). It is only for local smoke testing and UI demos; it is not the 100,000-query grading dataset. The grading dataset source remains AmazonQAC.

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
Search events received: 500
DB write operations: 5
Writes saved: 495
Write reduction: 99.00%
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
Suggest requests: 201
Cache hit rate: 91.54%
Average suggest latency: 0.91 ms
P95 suggest latency: 1.62 ms
Search events: 500
DB writes: 5
Writes saved: 495
Write reduction: 99.00%
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

## Submission Notes

- AmazonQAC full train data is not committed because the dataset is large.
- The public GitHub repository contains the runnable source code, Markdown report source, and generated PDF report.
- The included screenshots and smoke benchmark were generated from the evaluator-friendly local profile.
