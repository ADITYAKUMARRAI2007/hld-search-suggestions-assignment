# Codex Prompt — Production Java HLD Search Suggestions Assignment

You are working in:

`/Users/adityakumarrai/.openclaw/workspace/hld-search-suggestions-assignment`

## Important Context

This is a search typeahead / autocomplete HLD assignment. It must be implemented in Java and should be presented as a production-ready system design, not a Python/SQLite local toy.

Optimize for the evaluator's rubric:

- 60 marks: basic system, dataset ingestion, search UI, suggestions/search APIs, query-count updates, distributed cache using consistent hashing.
- 20 marks: trending searches with clear scoring/windowing logic.
- 20 marks: batch writes with evidence of write reduction and failure trade-offs.

## Final Architecture Choice

Use:

- Java 21
- Spring Boot
- PostgreSQL as source-of-truth query-count store
- OpenSearch as prefix/autocomplete read model
- Redis shards as distributed prefix cache
- Java application-level consistent hash ring to route prefix cache keys to Redis shards
- Kafka as durable search-event buffer
- Spring Kafka batch worker to aggregate repeated queries before DB writes
- Micrometer + Spring Boot Actuator for metrics
- Docker Compose for evaluator-friendly execution
- AmazonQAC as the real dataset source

## Required APIs

### `GET /api/v1/suggest?q=iph&mode=hybrid`

Return top 10 suggestions that start with the prefix.

Response shape:

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

- Normalize prefix.
- Route cache key with `ConsistentHashRing`.
- Check selected Redis shard first.
- On miss, query OpenSearch completion suggester.
- Cache result with TTL.
- Return cache hit/miss, node, key, and latency.

### `POST /api/v1/search`

Request:

```json
{ "query": "iphone 15" }
```

Behavior:

- Normalize query.
- Publish search event to Kafka.
- Return dummy search response quickly.
- Do not synchronously write every search to PostgreSQL.

Response:

```json
{
  "message": "Searched",
  "status": "accepted",
  "query": "iphone 15"
}
```

### `GET /api/v1/trending?window=1h&limit=10`

Return top recent searches from time-windowed trend counters.

### `GET /api/v1/cache/debug?prefix=iph&mode=hybrid`

Show selected cache node, hit/miss, TTL, key, and ring details.

### `GET /api/v1/metrics/summary`

Expose assignment-friendly metrics:

- suggest requests
- cache hits
- cache misses
- cache hit rate
- average suggestion latency
- p95 suggestion latency
- search events received
- Kafka events published
- DB write operations
- writes saved by batching
- write reduction percent
- batch flush count
- cache node distribution

Also expose `/actuator/health`, `/actuator/metrics`, and `/actuator/prometheus`.

## Dataset

Use the real AmazonQAC dataset:

`https://huggingface.co/datasets/amazon/AmazonQAC`

Important fields:

- `final_search_term` -> query text
- `popularity` -> historical count
- `search_time` -> trend seeding
- `prefixes` -> validation/explanation of autocomplete suitability

Do not commit the full dataset. Build/document a Java import path that can load at least 100,000 unique queries from a local AmazonQAC CSV/JSONL export.

## Key Implementation Requirements

### Consistent Hashing

Implement a real Java `ConsistentHashRing`:

- physical cache nodes
- virtual nodes
- deterministic key-to-node routing
- remapping test when a node is added/removed

Use it in production code for Redis shard selection.

### Cache

- Cache prefix results in Redis.
- Cache key format: `suggest:v1:{mode}:{normalized_prefix}`.
- Include TTL.
- Invalidate affected prefixes after batch updates.

### OpenSearch

Use a completion field:

- `input`: query text
- `weight`: hybrid score

OpenSearch serves cache misses.

### Trending

Use time-windowed buckets:

- 1h
- 24h
- 7d if easy

Hybrid ranking should combine historical and recent counts:

```text
score =
  log10(historical_count + 1) * 100000
  + log10(recent_count_24h + 1) * 25000
  + log10(recent_count_1h + 1) * 50000
```

### Batch Writes

Use Kafka as durable event buffer.

Batch worker:

- consumes search events
- aggregates repeated queries
- flushes by interval or batch size
- bulk upserts PostgreSQL
- updates trend buckets
- bulk updates OpenSearch weights
- invalidates Redis prefix keys
- records write-reduction metrics

## Frontend

Build a clean usable app:

- Search input
- Debounced suggestions
- Suggestion dropdown
- Keyboard navigation
- Search submit on Enter/button
- Trending panel
- Cache debug panel
- Metrics panel
- Loading/error states

## Documentation

README must include:

1. Problem statement.
2. Production architecture diagram.
3. Java/Spring Boot stack explanation.
4. Why PostgreSQL, OpenSearch, Redis, and Kafka were chosen.
5. Dataset source and loading instructions.
6. API examples.
7. Consistent hashing explanation.
8. Trending scoring/windowing explanation.
9. Batch-write design and failure trade-offs.
10. Metrics/performance report.
11. Setup with Docker Compose.
12. Demo screenshots or video instructions.
13. Rubric checklist.

## Validation

Run:

```bash
mvn test
mvn spring-boot:run
```

Verify:

```bash
curl 'http://127.0.0.1:8080/api/v1/suggest?q=iph'
curl -X POST 'http://127.0.0.1:8080/api/v1/search' \
  -H 'Content-Type: application/json' \
  -d '{"query":"iphone 15"}'
curl 'http://127.0.0.1:8080/api/v1/trending'
curl 'http://127.0.0.1:8080/api/v1/cache/debug?prefix=iph'
curl 'http://127.0.0.1:8080/api/v1/metrics/summary'
```

## Final Output Expected

Leave this folder with:

- Java Spring Boot source code.
- Docker Compose infrastructure.
- Real dataset loading instructions.
- Working APIs.
- Working UI.
- Consistent hashing cache implementation.
- Kafka-backed batch writes.
- Trending searches.
- Metrics proving latency, cache hit rate, and write reduction.
- README and docs that clearly map to the rubric.
