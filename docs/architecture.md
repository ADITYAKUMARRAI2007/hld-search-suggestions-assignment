# HLD Architecture

This is the system architecture, not a repository/package diagram. The goal is to show how a production search typeahead service handles low-latency prefix reads, durable query-count writes, recency-aware ranking, and distributed caching.

## Production Deployment View

```text
Users / Browser
  |
  v
CDN / Load Balancer / API Gateway
  |
  v
Spring Boot API Service Replicas
  |---- GET /suggest -----------------------------|
  |                                                v
  |                                      Suggestion Service
  |                                                |
  |                                      normalize prefix
  |                                                |
  |                                      Consistent Hash Ring
  |                                                |
  |                                      Redis Prefix Cache Shards
  |                                      /       |        \
  |                             cache-node-1 cache-node-2 cache-node-3
  |                                                |
  |                                      cache miss fallback
  |                                                v
  |                                      OpenSearch Suggestion Index
  |
  |---- POST /search ------------------------------|
                                                   v
                                           Kafka search-events
                                                   |
                                                   v
                                           Batch Writer Workers
                                           |       |       |
                                           v       v       v
                                    PostgreSQL  OpenSearch  Redis invalidation
                                    counts +    weight      affected prefix
                                    trend       refresh     cache keys
                                    buckets

Dataset import: AmazonQAC CSV/JSONL -> PostgreSQL query_catalog -> OpenSearch suggestion index
Observability: Actuator + Micrometer metrics -> Prometheus/Grafana-ready endpoints
```

## Why This Architecture Fits Typeahead

| Requirement | Architecture Decision | Reason |
|---|---|---|
| Top 10 prefix suggestions with low latency | Redis prefix cache before OpenSearch | Hot prefixes repeat heavily, so cache hits avoid index calls. |
| Suggestions sorted by popularity | OpenSearch weighted completion index | Completion suggester supports prefix lookup and weight-based ranking. |
| Query-count updates | PostgreSQL source of truth | Counts need durable upserts and auditability. |
| Distributed cache with consistent hashing | Java hash ring over Redis shards | Shows deterministic node ownership and limited remapping when cache nodes change. |
| Trending searches | Kafka events + 5-minute PostgreSQL-backed trend buckets in Docker profile | Recent activity can affect rank without permanently overriding history. |
| Batch writes | Kafka + batch workers | Repeated searches become one aggregated database/index update per flush. |
| Performance evidence | Metrics endpoint | Exposes latency, p95, cache hit rate, DB writes, and write reduction. |

## Suggestion Read Path

```text
GET /suggest?q=iph&rank=hybrid
  -> normalize q to "iph"
  -> build cache key suggest:v1:{version}:hybrid:iph
  -> consistent hash ring selects one Redis cache node
  -> cache hit: return cached top 10
  -> cache miss: query OpenSearch completion suggester
  -> cache top 10 with TTL
  -> return suggestions + cache debug + latency
```

The same endpoint supports two ranking modes:

- `rank=count`: basic version sorted by all-time historical count.
- `rank=hybrid`: enhanced version using historical and recent activity.

## Search Write Path

```text
POST /search
  -> validate and normalize query
  -> publish search event to Kafka
  -> return "Searched" quickly

Batch writer:
  -> consume events from Kafka
  -> aggregate repeated queries
  -> bulk upsert PostgreSQL counts
  -> update 5-minute PostgreSQL trend buckets
  -> refresh OpenSearch suggestion weights
  -> invalidate affected Redis prefix cache keys
```

This keeps search submission fast while still making updates eventually visible in suggestions and trending.

## Trending Ranking

Recent activity is tracked in 5-minute buckets and queried as `1h`, `24h`, and `7d` windows.

```text
hybrid_score =
  log10(historical_count + 1) * 100000
  + log10(recent_count_1h + 1) * 60000
  + log10(recent_count_24h + 1) * 25000
```

This prevents permanent over-ranking because recent counts expire from the active window. A short TTL on hybrid cache entries plus prefix invalidation after batch flushes keeps the ranking fresh.

In the Docker profile, trend buckets are written to PostgreSQL. In the default smoke profile, the same store interface uses memory so the system can run on a fresh desktop without external services.

## Consistent Hashing

The cache ring uses 128 virtual nodes per Redis shard. A prefix cache key is hashed using SHA-256 and assigned to the first virtual node clockwise.

Benefits:

- Even distribution across cache nodes.
- Adding/removing a node remaps only part of the keyspace.
- Lower cache churn than modulo hashing.
- `/cache/debug` proves which node owns a prefix.

## Runnable Profiles

| Runtime | Purpose | Storage/Search/Cache/Eventing |
|---|---|---|
| Default smoke profile | Fast evaluator demo without external services | In-memory query store, trend buckets, index, cache, and event queue |
| Docker/production profile | Production-like architecture on one machine | PostgreSQL query store + trend buckets, OpenSearch, Redis shards, Kafka |
