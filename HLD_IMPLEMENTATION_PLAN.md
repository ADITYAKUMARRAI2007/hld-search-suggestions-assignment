# Search Typeahead HLD Implementation Plan

## Decision

Yes, we will build this in Java and frame it as a production-grade search typeahead system.

The project should still be runnable for evaluation, but the design should not look like a local-only toy. The codebase will use production-style components, clear interfaces, durable write buffering, real distributed cache behavior, and proper observability.

Final chosen stack:

| Layer | Choice | Why This Choice |
|---|---|---|
| Backend language | Java 21 | Current LTS, strong ecosystem, appropriate for HLD/backend assignments. |
| Backend framework | Spring Boot | Production-ready Java web/API framework with validation, dependency injection, metrics, health checks, and OpenAPI integration. |
| API style | REST JSON | Simple for the evaluator to test with browser, curl, Swagger UI, and frontend. |
| Source-of-truth DB | PostgreSQL | Durable query metadata, transactional upserts, easy local/prod deployment, strong correctness story. |
| Search read model | OpenSearch | Purpose-built search index for prefix/autocomplete retrieval and ranked suggestions. |
| Cache | Redis shards behind an application-level consistent hash ring | Low-latency prefix-result cache while visibly satisfying the consistent hashing requirement. |
| Event buffer | Kafka | Durable search-event log for batch writes; avoids losing events if the API server crashes. |
| Batch aggregation | Java Kafka consumer / Spring Kafka worker | Aggregates repeated queries before writing to PostgreSQL/OpenSearch. |
| Metrics | Micrometer + Spring Boot Actuator | Latency, p95, cache hit rate, Kafka lag, DB write reduction, health endpoints. |
| Frontend | Vanilla HTML/CSS/JS or Thymeleaf-served static page | Keeps UI simple while demonstrating all backend concepts. |
| Deployment demo | Docker Compose | Runs PostgreSQL, OpenSearch, Kafka, Redis shards, and Java app consistently. |
| Production deployment target | Kubernetes-ready containers | HLD should explain horizontal scaling, replicas, probes, config, and managed service alternatives. |

## Rubric Mapping

| Rubric Area | Marks | Production-Grade Deliverable |
|---|---:|---|
| Basic implementation | 60 | Java Spring Boot API, real dataset ingestion, search UI, `/suggest`, `/search`, query-count updates, OpenSearch read model, PostgreSQL source of truth, Redis distributed cache using consistent hashing. |
| Trending searches | 20 | Time-windowed trending counters, `/trending`, hybrid ranking formula, decay/windowing explanation, cache invalidation on ranking updates. |
| Batch writes | 20 | Kafka-backed event ingestion, batch aggregation by query, bulk DB/OpenSearch updates, metrics proving write reduction, failure trade-off discussion. |

## Architecture

```text
Browser / Demo UI
  -> Spring Boot API Service
      -> Suggestion Service
          -> ConsistentHashRing
          -> Redis Prefix Cache Shards
          -> OpenSearch Suggestion Index
          -> Ranking Service
      -> Search Submission Service
          -> Kafka Producer
      -> Trending Service
          -> Redis / PostgreSQL trend buckets
      -> Metrics Service
          -> Micrometer + Actuator

Kafka: search-events topic
  -> Batch Aggregator Worker
      -> aggregate repeated queries
      -> PostgreSQL query_catalog upsert
      -> PostgreSQL query_trend_buckets upsert
      -> OpenSearch bulk weight update
      -> Redis prefix cache invalidation
      -> metrics update
```

Production view:

```text
CDN / Browser
  -> Load Balancer / API Gateway
  -> N Spring Boot API replicas
  -> Redis distributed cache
  -> OpenSearch cluster
  -> Kafka cluster
  -> Batch worker replicas
  -> PostgreSQL primary + read replicas
  -> Prometheus/Grafana/Logs/Tracing
```

## Core Request Flows

### Suggestion Flow

```text
User types "iph"
  -> GET /api/v1/suggest?q=iph&mode=hybrid
  -> validate and normalize prefix
  -> build cache key: suggest:v1:hybrid:iph
  -> ConsistentHashRing maps key to cache-node-N
  -> read Redis shard selected by the ring
  -> if cache hit, return top 10 suggestions with cache debug metadata
  -> if cache miss, query OpenSearch completion suggester
  -> rank/shape results
  -> store top 10 in Redis with TTL
  -> return suggestions, cache hit/miss, selected node, and latency
```

### Search Submission Flow

```text
User submits "iphone 15"
  -> POST /api/v1/search
  -> validate and normalize query
  -> produce event to Kafka topic search-events with key=normalized query
  -> return {"message": "Searched", "status": "accepted"}
  -> batch worker consumes events
  -> aggregates counts per query
  -> bulk-upserts PostgreSQL query counts
  -> updates trend buckets
  -> bulk-updates OpenSearch suggestion weights
  -> invalidates affected prefix cache keys
```

Why Kafka instead of an in-memory buffer:

- The assignment asks for batch writes, but production systems should avoid losing accepted searches when an app process dies.
- Kafka gives a durable log, replay, partitioned scale, and ordered events per query key.
- We still measure the same write-reduction metric: raw search events versus unique DB updates after aggregation.

## Dataset Decision

Primary real dataset:

- Dataset: AmazonQAC
- Source: `https://huggingface.co/datasets/amazon/AmazonQAC`
- License: CDLA-Permissive-2.0
- Format: Parquet
- Size: 396M rows
- Domain: real-world query autocomplete from Amazon Search logs
- Useful fields:
  - `final_search_term`: canonical query text
  - `popularity`: initial historical count/frequency
  - `search_time`: used to seed recent/trending windows
  - `prefixes`: actual typed prefixes, useful for dataset explanation and validation

Why AmazonQAC is the best assignment dataset:

- It is specifically a query-autocomplete dataset, not a random product/title dataset.
- It includes real prefixes typed by users.
- It includes a popularity signal, so initial counts are not invented.
- It is much larger than the required 100,000 queries.
- It has timestamps for trending-search logic.

Dataset loading strategy:

1. The Java importer accepts a local AmazonQAC CSV/JSONL export.
2. It streams records rather than loading the full dataset into memory.
3. It normalizes `final_search_term`.
4. It deduplicates by normalized query.
5. It writes at least 100,000 unique queries to PostgreSQL.
6. It indexes the same queries into OpenSearch.
7. It records dataset metadata in `dataset_import_runs`.

Minimum loaded fields:

```text
final_search_term -> query_catalog.display_query / normalized_query
popularity        -> query_catalog.historical_count
search_time       -> query_catalog.last_searched_at and trend bucket seed
prefixes          -> optional validation/reporting metadata
```

Fallback real dataset:

- ORCAS from MS MARCO: `https://microsoft.github.io/msmarco/ORCAS.html`
- Good fallback because counts can be derived by aggregating repeated real queries.
- AmazonQAC remains the preferred dataset because it is made for autocomplete.

Do not commit the full dataset. Commit:

- importer code
- exact dataset URL
- loading instructions
- metadata sample
- optional tiny smoke-test fixture clearly marked as non-grading data

## Data Model

### PostgreSQL Tables

`query_catalog`

```sql
CREATE TABLE query_catalog (
  id BIGSERIAL PRIMARY KEY,
  normalized_query TEXT NOT NULL UNIQUE,
  display_query TEXT NOT NULL,
  historical_count BIGINT NOT NULL DEFAULT 0,
  recent_count_1h BIGINT NOT NULL DEFAULT 0,
  recent_count_24h BIGINT NOT NULL DEFAULT 0,
  last_searched_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_query_catalog_normalized_prefix
  ON query_catalog (normalized_query text_pattern_ops);

CREATE INDEX idx_query_catalog_historical_count
  ON query_catalog (historical_count DESC);
```

`query_trend_buckets`

```sql
CREATE TABLE query_trend_buckets (
  bucket_start TIMESTAMPTZ NOT NULL,
  bucket_minutes INT NOT NULL,
  normalized_query TEXT NOT NULL,
  search_count BIGINT NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (bucket_start, bucket_minutes, normalized_query)
);

CREATE INDEX idx_query_trend_bucket_count
  ON query_trend_buckets (bucket_minutes, bucket_start DESC, search_count DESC);
```

`dataset_import_runs`

```sql
CREATE TABLE dataset_import_runs (
  id BIGSERIAL PRIMARY KEY,
  dataset_name TEXT NOT NULL,
  dataset_url TEXT NOT NULL,
  source_format TEXT NOT NULL,
  rows_read BIGINT NOT NULL,
  unique_queries_loaded BIGINT NOT NULL,
  started_at TIMESTAMPTZ NOT NULL,
  finished_at TIMESTAMPTZ,
  status TEXT NOT NULL
);
```

`batch_flush_audit`

```sql
CREATE TABLE batch_flush_audit (
  id BIGSERIAL PRIMARY KEY,
  raw_events BIGINT NOT NULL,
  unique_queries BIGINT NOT NULL,
  db_write_operations BIGINT NOT NULL,
  writes_saved BIGINT NOT NULL,
  started_at TIMESTAMPTZ NOT NULL,
  finished_at TIMESTAMPTZ NOT NULL
);
```

Why PostgreSQL:

- Query counts need correctness, upserts, auditability, and easy inspection.
- Batch writes reduce pressure enough for the assignment and many real deployments.
- PostgreSQL is simple to run in Docker and defensible in production with partitioning, connection pooling, read replicas, backups, and managed Postgres.
- At very large global scale, the HLD can mention replacing this count store with DynamoDB, Cassandra, or ScyllaDB while keeping Kafka, Redis, and OpenSearch unchanged.

## OpenSearch Read Model

Index: `query_suggestions_v1`

Use OpenSearch completion field because the assignment requires suggestions that start with the typed prefix.

Mapping idea:

```json
{
  "mappings": {
    "properties": {
      "normalized_query": { "type": "keyword" },
      "display_query": { "type": "keyword" },
      "suggestions": {
        "type": "completion",
        "analyzer": "simple",
        "preserve_separators": true
      },
      "historical_count": { "type": "long" },
      "recent_count_1h": { "type": "long" },
      "recent_count_24h": { "type": "long" },
      "hybrid_weight": { "type": "integer" },
      "updated_at": { "type": "date" }
    }
  }
}
```

Document shape:

```json
{
  "normalized_query": "iphone 15",
  "display_query": "iphone 15",
  "suggestions": {
    "input": "iphone 15",
    "weight": 918240
  },
  "historical_count": 850000,
  "recent_count_1h": 312,
  "recent_count_24h": 1205,
  "hybrid_weight": 918240,
  "updated_at": "2026-06-21T12:00:00Z"
}
```

Why OpenSearch:

- Prefix retrieval should not rely on scanning the primary DB.
- OpenSearch completion suggester is built for prefix autocomplete and supports weights for ranking.
- It is horizontally scalable, replicated, and familiar in production search systems.
- Redis handles hot prefixes; OpenSearch handles cache misses and rebuilds.

OpenSearch ranking:

```text
hybrid_weight = round(
  log10(historical_count + 1) * 100000
  + log10(recent_count_24h + 1) * 25000
  + log10(recent_count_1h + 1) * 50000
)
```

Why logarithms:

- Very popular historical queries stay important.
- Recent spikes can move a query up without permanently dominating.
- Extreme counts do not completely crush smaller but fresh queries.

## Distributed Cache Design

Cache storage:

- Redis shard 1
- Redis shard 2
- Redis shard 3

Application routing:

- Java `ConsistentHashRing` maps cache keys to a logical cache node.
- Each node has many virtual nodes, for example 128 or 256.
- Each logical node points to one Redis connection.
- Cache keys are deterministic and include ranking mode/version.

Cache key:

```text
suggest:v1:{mode}:{normalized_prefix}
```

Cached value:

```json
{
  "prefix": "iph",
  "mode": "hybrid",
  "suggestions": [
    {
      "query": "iphone 15",
      "count": 850000,
      "recent_count_1h": 312,
      "recent_count_24h": 1205,
      "score": 918240
    }
  ],
  "generated_at": "2026-06-21T12:00:00Z"
}
```

TTL:

- Hot prefix suggestions: 30-120 seconds.
- Trending list: 15-60 seconds.
- Cache version included in key so ranking formula changes do not reuse stale data.

Invalidation:

- When batch worker updates a query, compute all prefixes for that query up to a practical max length.
- Delete keys for affected prefixes and modes.
- Also allow coarse invalidation by bumping a cache namespace version if large updates are loaded.

Why Redis and consistent hashing:

- Typeahead is read-heavy and hot-prefix-heavy.
- Redis gives sub-millisecond in-memory retrieval for cached prefix results.
- Consistent hashing visibly satisfies the assignment: adding/removing a cache node remaps only part of the keyspace.
- The `/cache/debug` endpoint will prove which node owns a prefix and whether it is a hit.

Production note:

- Managed Redis Cluster can replace the three Docker Redis shards.
- Redis Cluster uses hash slots internally; our application ring is kept because the assignment explicitly asks to implement/explain consistent hashing.

## Java Package Structure

```text
hld-search-suggestions-assignment/
  pom.xml
  docker-compose.yml
  README.md
  HLD_IMPLEMENTATION_PLAN.md
  docs/
    architecture.md
    api.md
    performance-report.md
    tradeoffs.md
    screenshots/
  src/
    main/
      java/com/example/typeahead/
        TypeaheadApplication.java
        api/
          SuggestionController.java
          SearchController.java
          TrendingController.java
          CacheDebugController.java
          MetricsController.java
        cache/
          CacheNode.java
          CacheNodeConfig.java
          ConsistentHashRing.java
          PrefixCache.java
          RedisPrefixCache.java
        config/
          KafkaConfig.java
          OpenSearchConfig.java
          RedisShardConfig.java
          WebConfig.java
        dataset/
          AmazonQacImportCommand.java
          DatasetImportService.java
          DatasetRecord.java
        metrics/
          TypeaheadMetrics.java
          MetricsSnapshotService.java
        model/
          Suggestion.java
          SuggestResponse.java
          SearchRequest.java
          SearchResponse.java
          TrendingResponse.java
        normalize/
          QueryNormalizer.java
        ranking/
          RankingMode.java
          RankingService.java
        search/
          SearchEvent.java
          SearchSubmissionService.java
          SearchEventProducer.java
        suggestion/
          SuggestionService.java
          OpenSearchSuggestionRepository.java
        trending/
          TrendingService.java
        worker/
          SearchEventConsumer.java
          BatchAggregator.java
          BatchFlushService.java
          CacheInvalidationService.java
      resources/
        application.yml
        db/migration/
          V1__init.sql
        opensearch/
          query_suggestions_mapping.json
        static/
          index.html
          styles.css
          app.js
    test/
      java/com/example/typeahead/
        cache/ConsistentHashRingTest.java
        normalize/QueryNormalizerTest.java
        ranking/RankingServiceTest.java
        suggestion/SuggestionServiceTest.java
```

## API Plan

### `GET /api/v1/suggest?q=<prefix>&mode=hybrid`

Returns up to 10 prefix suggestions.

Modes:

- `count`: historical count only.
- `recent`: recent activity only.
- `hybrid`: production default combining history and recency.

Response:

```json
{
  "query": "iph",
  "mode": "hybrid",
  "suggestions": [
    {
      "query": "iphone 15",
      "count": 850000,
      "recent_count_1h": 312,
      "recent_count_24h": 1205,
      "score": 918240
    }
  ],
  "cache": {
    "hit": true,
    "node": "cache-node-2",
    "key": "suggest:v1:hybrid:iph"
  },
  "latency_ms": 3.7
}
```

Behavior:

- Empty/missing prefix returns an empty suggestion list plus trending hint if desired.
- Prefix is normalized lowercase.
- Results must start with the prefix.
- Cache is checked before OpenSearch.
- The first request for a prefix should show miss; repeated request should show hit.

### `POST /api/v1/search`

Request:

```json
{ "query": "iphone 15" }
```

Response:

```json
{
  "message": "Searched",
  "status": "accepted",
  "query": "iphone 15",
  "event_id": "01J..."
}
```

Behavior:

- Validate query length and characters.
- Normalize query.
- Publish to Kafka.
- Return after Kafka acknowledges write.
- Does not synchronously update PostgreSQL/OpenSearch.

### `GET /api/v1/trending?window=1h&limit=10`

Response:

```json
{
  "window": "1h",
  "trending": [
    {
      "query": "iphone 15",
      "recent_count": 312,
      "historical_count": 850000,
      "score": 312
    }
  ]
}
```

Behavior:

- Reads hot trending list from Redis if cached.
- Falls back to PostgreSQL trend bucket aggregation.
- Supports `1h`, `24h`, and `7d` windows.

### `GET /api/v1/cache/debug?prefix=<prefix>&mode=hybrid`

Response:

```json
{
  "prefix": "iph",
  "normalized_prefix": "iph",
  "cache_key": "suggest:v1:hybrid:iph",
  "node": "cache-node-2",
  "hit": true,
  "ttl_seconds_remaining": 48,
  "ring": {
    "physical_nodes": 3,
    "virtual_nodes_per_node": 128,
    "hash_algorithm": "SHA-256"
  }
}
```

### `GET /api/v1/metrics/summary`

Assignment-friendly metrics response:

```json
{
  "suggest_requests": 1000,
  "cache_hits": 820,
  "cache_misses": 180,
  "cache_hit_rate": 0.82,
  "avg_suggest_latency_ms": 4.1,
  "p95_suggest_latency_ms": 11.8,
  "search_events_received": 5000,
  "kafka_events_published": 5000,
  "db_write_operations": 430,
  "writes_saved_by_batching": 4570,
  "write_reduction_percent": 91.4,
  "batch_flushes": 52,
  "last_flush_unique_queries": 31,
  "cache_node_distribution": {
    "cache-node-1": 351,
    "cache-node-2": 329,
    "cache-node-3": 320
  }
}
```

Also expose:

- `/actuator/health`
- `/actuator/metrics`
- `/actuator/prometheus`
- Swagger/OpenAPI UI

### Admin/Demo Endpoints

These are useful for demo and should be clearly marked as non-public/admin:

- `POST /api/v1/admin/flush` forces a batch flush.
- `POST /api/v1/admin/reindex` rebuilds OpenSearch from PostgreSQL.
- `POST /api/v1/admin/cache/clear` clears prefix caches.

## Trending Design

We will not use a single permanent `recent_count` only. That is too simplistic for a production answer.

Production model:

- Search events enter Kafka.
- Worker aggregates by normalized query.
- Worker writes counts into time buckets:
  - 5-minute bucket for freshness.
  - 1-hour rollup for live trending.
  - 24-hour rollup for stable trending.
- Redis stores top trending lists as sorted sets:
  - `trending:1h`
  - `trending:24h`
  - `trending:7d`

Suggestion ranking uses hybrid score:

```text
score =
  log10(historical_count + 1) * 100000
  + log10(recent_count_24h + 1) * 25000
  + log10(recent_count_1h + 1) * 50000
```

How this avoids stale trends:

- Recent counters are windowed, not permanent.
- Old buckets naturally expire or are ignored after the window passes.
- Redis trending sorted sets have TTL.
- OpenSearch weights are refreshed by batch worker after aggregated updates.

Trade-offs:

- More fresh rankings require more frequent OpenSearch updates and cache invalidations.
- Longer cache TTL improves latency and hit rate but may show stale rankings briefly.
- A 30-60 second cache TTL is a good assignment/demo balance.

## Batch Write Design

### Production Path

```text
POST /search
  -> Kafka search-events topic
  -> BatchAggregator groups events by query for 5 seconds or 1000 events
  -> PostgreSQL bulk upsert counts
  -> PostgreSQL trend bucket upsert
  -> OpenSearch bulk update suggestion weights
  -> Redis prefix cache invalidation
```

Kafka topic:

```text
name: search-events
partitions: 6 or 12
replication.factor: 3 in production
key: normalized_query
value: SearchEvent JSON
```

Search event:

```json
{
  "event_id": "01J...",
  "normalized_query": "iphone 15",
  "display_query": "iphone 15",
  "occurred_at": "2026-06-21T12:00:00Z",
  "source": "web"
}
```

Aggregation example:

```text
Raw events in 5 seconds:
  iphone 15 -> 100 events
  airpods -> 40 events
  laptop stand -> 10 events

DB writes:
  3 upserts instead of 150 writes

Writes saved:
  150 - 3 = 147
  write reduction = 98%
```

Failure trade-offs:

- API crash after Kafka ack: event is safe in Kafka.
- API crash before Kafka ack: request fails or client retries.
- Worker crash before commit: Kafka redelivers; use idempotent aggregation/upsert strategy.
- DB failure: worker retries and Kafka lag grows.
- Redis invalidation failure: stale cache lasts only until TTL or next invalidation retry.
- OpenSearch update failure: PostgreSQL remains source of truth; reindex job repairs read model.

## Consistent Hashing Details

Java class:

```text
ConsistentHashRing
  - addNode(CacheNode node)
  - removeNode(String nodeId)
  - getNode(String key)
  - getDistribution(Collection<String> keys)
```

Implementation choice:

- Use SHA-256 or Murmur3 hash.
- Add 128 virtual nodes per physical cache node.
- Store virtual node positions in `NavigableMap<Long, CacheNode>`.
- For a key, hash to a position and choose the first node clockwise.
- Wrap to the first ring entry when needed.

Why this matters:

- With modulo hashing, changing from 3 to 4 nodes remaps most keys.
- With consistent hashing, only keys owned by affected ranges move.
- That means fewer cold cache misses when scaling cache nodes.

Demo evidence:

- `/api/v1/cache/debug?prefix=iph` shows selected node.
- `/api/v1/metrics/summary` shows cache node distribution.
- A unit test should prove adding a node remaps only a fraction of sample keys.

## Frontend Requirements

The first screen should be the actual search app, not a landing page.

UI features:

- Search input.
- Debounced API calls.
- Suggestion dropdown.
- Keyboard navigation: ArrowUp, ArrowDown, Enter, Escape.
- Search button.
- Trending searches panel.
- Search response status.
- Cache debug panel: hit/miss, node, latency, key.
- Metrics panel: cache hit rate, p95 latency, write reduction.
- Loading and error states.

Demo flow:

1. Type `iph`.
2. Show suggestions.
3. Show cache miss and selected Redis node.
4. Repeat the prefix.
5. Show cache hit.
6. Submit `iphone 15` many times.
7. Show `/metrics/summary` write reduction.
8. Show trending update after batch flush.

## Production Readiness Checklist

Code quality:

- Layered packages and clear service boundaries.
- DTO validation with `jakarta.validation`.
- No business logic in controllers.
- Interfaces around cache, search index, event producer, and repositories.
- Integration tests using Testcontainers if time allows.
- Flyway migrations for database schema.

Reliability:

- Kafka retry/backoff.
- Idempotent upserts.
- OpenSearch bulk retry.
- Redis timeout and fallback to OpenSearch.
- Circuit-breaker/timeouts for external systems.
- Health checks for Postgres, Redis, Kafka, and OpenSearch.

Security:

- Input validation and max query length.
- Rate limit `/suggest` and `/search`.
- CORS restricted in production profile.
- No secrets in repo.
- Admin endpoints protected or disabled by default.

Observability:

- Request latency histogram.
- Cache hit/miss counters.
- OpenSearch miss latency.
- Kafka publish latency.
- Consumer lag.
- Batch flush size and duration.
- DB writes and writes saved.
- Error counters by dependency.

Scalability:

- API service is stateless and horizontally scalable.
- Kafka partitions scale search-event ingestion.
- Batch workers scale by consumer group.
- Redis shards scale prefix cache.
- OpenSearch scales query read model.
- PostgreSQL can use partitioning/read replicas; high-scale alternative is Cassandra/DynamoDB/ScyllaDB for counters.

## Performance Report Plan

Create `docs/performance-report.md`.

Report required by assignment:

- Dataset source and loaded query count.
- Average suggestion latency.
- p95 suggestion latency.
- Cache hit rate.
- Search events received.
- DB write operations.
- Writes saved by batching.
- Write reduction percentage.
- Cache node distribution.

Benchmark script options:

- Java JUnit/performance runner.
- Shell script with `curl`.
- k6 script if we want a stronger report.

Suggested benchmark:

```text
1. Load 100,000+ AmazonQAC queries.
2. Warm cache with 50 common prefixes.
3. Send 1,000 suggest requests with repeated prefixes.
4. Send 5,000 search submissions with duplicate-heavy distribution.
5. Wait for batch flush.
6. Capture /api/v1/metrics/summary.
```

Expected evidence shape:

```text
Dataset: AmazonQAC
Unique queries loaded: 100,000+
Suggest requests: 1,000
Cache hit rate: 80%+
Average suggest latency: < 20 ms in Docker after warmup
p95 suggest latency: report actual value
Search events: 5,000
DB write operations: far below raw events
Write reduction: report actual percentage
```

## Documentation Plan

README must include:

1. Problem statement.
2. Production architecture diagram.
3. Why Java/Spring Boot.
4. Why PostgreSQL, OpenSearch, Redis, Kafka.
5. Dataset source and loading instructions.
6. API documentation and examples.
7. Consistent hashing explanation.
8. Cache invalidation and TTL explanation.
9. Trending scoring/windowing explanation.
10. Batch write design and failure trade-offs.
11. Metrics and performance report.
12. Setup commands using Docker Compose.
13. Screenshots/demo steps.
14. Rubric checklist.

Docs to create:

```text
docs/architecture.md
docs/api.md
docs/performance-report.md
docs/tradeoffs.md
docs/screenshots/
```

## Implementation Milestones

### Milestone 1: Java project skeleton

- Create Maven Spring Boot project.
- Add dependencies:
  - Spring Web
  - Spring Validation
  - Spring Data JDBC or JPA
  - PostgreSQL driver
  - Flyway
  - Spring Kafka
  - Spring Data Redis / Lettuce
  - OpenSearch Java client
  - Spring Boot Actuator
  - Micrometer Prometheus
  - springdoc-openapi
  - Testcontainers
- Add health endpoint and static UI route.

### Milestone 2: Docker Compose infrastructure

- PostgreSQL.
- Kafka.
- OpenSearch.
- Three Redis shard containers.
- Optional Prometheus.
- App container profile.

### Milestone 3: Database schema and repository layer

- Flyway migrations.
- Query catalog repository.
- Trend bucket repository.
- Batch audit repository.

### Milestone 4: Dataset importer

- Java import command for AmazonQAC sample/subset.
- Load 100,000+ unique queries into PostgreSQL.
- Bulk index into OpenSearch.
- Write dataset metadata.

### Milestone 5: OpenSearch suggestion index

- Create `query_suggestions_v1` mapping.
- Implement completion suggester query.
- Implement bulk indexing and weight updates.

### Milestone 6: Consistent hashing Redis cache

- Implement `ConsistentHashRing`.
- Implement `RedisPrefixCache`.
- Add `/cache/debug`.
- Add unit tests for distribution and remapping.

### Milestone 7: Suggestion API

- Implement `/api/v1/suggest`.
- Wire cache -> OpenSearch fallback.
- Add ranking mode support.
- Add latency and cache metrics.

### Milestone 8: Search API and Kafka

- Implement `/api/v1/search`.
- Publish durable search events to Kafka.
- Return dummy "Searched" response quickly.

### Milestone 9: Batch writer

- Implement Kafka consumer.
- Aggregate repeated queries by flush interval/size.
- Bulk upsert PostgreSQL counts.
- Update trend buckets.
- Bulk update OpenSearch weights.
- Invalidate affected cache prefixes.
- Record write-reduction metrics.

### Milestone 10: Trending API

- Implement `/api/v1/trending`.
- Use Redis sorted-set cache and PostgreSQL fallback.
- Support 1h/24h/7d windows.

### Milestone 11: Frontend

- Build usable search UI.
- Add debounced suggestions.
- Add keyboard support.
- Add trending/debug/metrics panels.

### Milestone 12: Tests, benchmark, docs

- Unit tests for normalizer, ranking, consistent hashing.
- Integration tests for API with Testcontainers if feasible.
- Benchmark and capture metrics.
- Final README and docs.
- Screenshots/demo recording.

## Key Design Answers for Viva

### Which database did we choose and why?

We use PostgreSQL as the source-of-truth query-count database because it gives durable transactions, upserts, indexes, auditability, and easy local/prod deployment. Reads do not depend on scanning PostgreSQL because OpenSearch and Redis serve the suggestion path. At very high global scale, the counter store can be moved to Cassandra, ScyllaDB, or DynamoDB without changing the API/cache/index design.

### Which cache did we choose and why?

We use Redis because prefix suggestions are small, hot, read-heavy values that benefit from in-memory retrieval. We route prefix keys across Redis shards using an application-level consistent hash ring so the assignment can clearly demonstrate distributed cache ownership, node remapping, and cache-node debug output.

### Why OpenSearch?

OpenSearch is the read-optimized suggestion index. Its completion suggester supports prefix autocomplete and weighted ranking. That gives faster and cleaner prefix retrieval than querying the primary database for every typed prefix.

### Why Kafka?

Kafka makes the write buffer durable. Instead of synchronously writing every search event to PostgreSQL, the API writes a small event to Kafka. A batch worker aggregates repeated queries and performs far fewer database writes. If the worker crashes, Kafka can replay events.

### How are top 10 suggestions fetched quickly?

The API first checks Redis by prefix. Cache hits return immediately. Cache misses query OpenSearch completion suggester, which is optimized for prefix autocomplete. Results are cached with TTL for repeated prefixes.

### How does consistent hashing work here?

Every Redis cache node is represented by many virtual nodes on a hash ring. A cache key is hashed onto the ring and assigned to the first node clockwise. Adding or removing a node moves only a portion of keys, reducing cache churn.

### How do trending searches work?

Search events are aggregated into time buckets such as 1 hour and 24 hours. `/trending` returns the highest-count queries in the selected window. Suggestion ranking combines historical count with recent bucket counts, so fresh searches can rise temporarily without being permanently over-ranked.

### How do batch writes reduce pressure?

If 1,000 search events contain only 80 unique queries during a flush interval, the worker performs about 80 count upserts instead of 1,000 individual writes. Metrics expose raw events, DB write operations, writes saved, and write reduction percentage.

### What are the failure trade-offs?

Kafka removes the biggest weakness of in-memory batching: accepted events are durable after Kafka ack. Remaining trade-offs are eventual consistency and temporary stale cache/search-index data. Redis TTL, cache invalidation, OpenSearch retries, and reindex jobs handle repair.

## External References to Cite in README

- AmazonQAC dataset: `https://huggingface.co/datasets/amazon/AmazonQAC`
- OpenSearch completion suggester: `https://docs.opensearch.org/latest/mappings/supported-field-types/completion/`
- OpenSearch search-as-you-type alternative: `https://docs.opensearch.org/latest/mappings/supported-field-types/search-as-you-type/`
- Redis Cluster reference: `https://redis.io/docs/latest/operate/oss_and_stack/reference/cluster-spec/`
- Apache Kafka introduction: `https://kafka.apache.org/intro/`
- Spring Boot Actuator production features: `https://docs.spring.io/spring-boot/reference/actuator/index.html`

## Done Criteria

The assignment is complete when:

- The Java project builds with Maven.
- Docker Compose starts the app and all dependencies.
- AmazonQAC loading instructions are documented.
- At least 100,000 real queries can be loaded.
- `/api/v1/suggest`, `/api/v1/search`, `/api/v1/trending`, `/api/v1/cache/debug`, and `/api/v1/metrics/summary` work.
- Suggestions are prefix-matching and top-10 ranked.
- Cache responses show hit/miss and consistent-hash node.
- Search submissions update counts eventually through Kafka batch writer.
- Trending searches work with time-window explanation.
- Metrics show latency, p95, cache hit rate, DB writes, and writes saved.
- README includes architecture, API docs, setup, dataset, screenshots/demo, trade-offs, and rubric mapping.
