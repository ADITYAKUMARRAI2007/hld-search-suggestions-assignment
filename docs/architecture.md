# Architecture

## Runtime Components

```text
Browser / Demo UI
  -> Spring Boot API Service
      -> SuggestionController
      -> SearchController
      -> TrendingController
      -> CacheDebugController
      -> MetricsController

SuggestionController
  -> QueryNormalizer
  -> CacheKeyService
  -> PrefixCache
      -> ConsistentHashRing
      -> cache-node-1/cache-node-2/cache-node-3
  -> SuggestionIndex
      -> OpenSearch in production profile
      -> in-memory index in smoke profile

SearchController
  -> SearchSubmissionService
  -> SearchEventPublisher
      -> Kafka in production profile
      -> in-memory publisher in smoke profile
  -> BatchAggregator
  -> QueryStore
      -> PostgreSQL in production profile
      -> in-memory store in smoke profile
  -> TrendBucketService
  -> SuggestionIndex refresh
  -> Prefix cache invalidation
```

## Read Path

1. User types a prefix.
2. API normalizes the prefix.
3. Cache key is built as `suggest:v1:{version}:{rank}:{prefix}`.
4. `ConsistentHashRing` routes the key to a cache node.
5. Cache hit returns top 10 immediately.
6. Cache miss queries the suggestion index and stores the result with TTL.

## Write Path

1. User submits a search.
2. API validates and normalizes the query.
3. Search event is published.
4. Batch worker aggregates repeated queries.
5. Worker updates query counts, trend buckets, suggestion weights, and cache invalidations.

## Consistent Hashing

The cache ring uses 128 virtual nodes per physical node. A cache key is hashed using SHA-256 and assigned to the first virtual node clockwise. Adding or removing cache nodes only remaps keys in affected ranges, avoiding the widespread cache churn caused by modulo hashing.

## Production Mapping

| Interface | Smoke Profile | Production/Docker Profile |
|---|---|---|
| QueryStore | In-memory map | PostgreSQL/JDBC |
| SuggestionIndex | In-memory prefix scan | OpenSearch completion suggester |
| PrefixCache | In-memory shards | Redis shards behind same hash ring |
| SearchEventPublisher | In-memory queue | Kafka |
