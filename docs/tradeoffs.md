# Design Choices And Trade-offs

## PostgreSQL For Source Of Truth

PostgreSQL gives durable upserts, indexes, transactional correctness, and easy inspection. It is not used as the hot prefix-read path; Redis and OpenSearch absorb read pressure. At very large global scale, the counter store could be moved to Cassandra, ScyllaDB, or DynamoDB.

## OpenSearch For Suggestions

OpenSearch completion suggesters are appropriate for prefix autocomplete and weighted ranking. This avoids scanning the primary database for every typed prefix. The trade-off is eventual consistency: weights are refreshed after batch flushes, not synchronously on every search.

## Redis Cache With Application Consistent Hashing

Redis is used because typeahead prefixes are small, hot, and read-heavy. The application-level ring is kept even though managed Redis Cluster has its own sharding because the assignment explicitly asks for consistent hashing. The trade-off is an extra routing layer, but it makes cache-node ownership and remapping visible.

## Kafka For Batch Writes

Kafka makes accepted search events durable. This is stronger than an in-memory-only batch buffer. The trade-off is eventual consistency: counts and trends update after the batch flush interval.

## Trending Windows

Recent activity is tracked in 5-minute buckets and queried as `1h`, `24h`, or `7d` windows. In the Docker profile those buckets are persisted in PostgreSQL; in the default smoke profile they use memory so the project can run without external services. This prevents short-lived spikes from being permanently over-ranked. The trade-off is that shorter windows are fresher but require more frequent cache invalidation and index refreshes.

## Cache Freshness

Hybrid ranking uses shorter TTLs than historical ranking because recent activity changes faster. Batch flushes invalidate prefixes for updated queries. If invalidation fails, TTL bounds staleness.
