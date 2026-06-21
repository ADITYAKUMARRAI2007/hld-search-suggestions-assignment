# HLD Assignment Brief — Search Suggestions System

## Goal

Build and present a **system design / HLD-style search suggestion system** with a runnable demo.

This assignment should optimize for the evaluator's rubric, not just for a working UI.

## Rubric focus

Total likely scoring:

- **60 marks**: basic working system + consistent hashing cache
- **20 marks**: trending searches
- **20 marks**: batch writes + evidence / write-reduction discussion

The submission should make the system design answers obvious from the code, README, and demo.

## What the evaluator will likely ask

Be ready to answer:

- How do you store query counts?
- How do you fetch top 10 prefix suggestions fast?
- Why cache prefixes?
- How does consistent hashing distribute cache keys?
- What happens when a cache node is added or removed?
- How do trending searches work?
- How do you batch writes?
- What are the trade-offs if the app crashes before flushing?
- How do you measure latency, cache hit rate, and write reduction?

## Recommended HLD architecture

```text
User / UI
  -> API Gateway / Backend
  -> Suggestion Service
  -> Distributed Prefix Cache
  -> Query Store / Prefix Index
  -> Metrics + Logs
```

For search submissions:

```text
User submits query
  -> Search API returns "Searched" quickly
  -> Write Buffer / Queue stores update
  -> Batch Writer periodically flushes aggregated counts
  -> DB updated
  -> Cache invalidated/refreshed
  -> Trending stats updated
```

## Components to build and explain

### 1. Frontend

Simple but clean:

- Search box
- Debounced API calls
- Suggestion dropdown
- Keyboard navigation
- Search button / Enter submit
- Trending section
- Debug area showing cache hit/miss/node

Frontend helps demonstrate the system, but it is not the main scoring area.

### 2. Suggestion API

Endpoint:

```text
GET /suggest?q=iph
```

Example response:

```json
{
  "query": "iph",
  "suggestions": [
    { "query": "iphone", "count": 100000, "score": 100000 },
    { "query": "iphone 15", "count": 85000, "score": 85000 }
  ],
  "cache": {
    "hit": true,
    "node": "cache-node-2"
  }
}
```

Explanation:

- User types a prefix.
- Backend normalizes it to lowercase.
- Backend first checks cache using the prefix as key.
- If cache miss, fetches matching queries from the store.
- Sorts by score/count.
- Stores top 10 in cache with TTL.
- Returns suggestions.

### 3. Data storage

For assignment simplicity:

- Use SQLite locally.
- Table: `queries(query, count, recent_count, updated_at)`
- Index on `query`.

For HLD explanation, production alternatives:

- Primary DB: PostgreSQL / Cassandra / DynamoDB
- Prefix index: Trie / Elasticsearch / Redis Sorted Sets / precomputed prefix table
- Cache: Redis Cluster

### 4. Consistent hashing cache

Implement a local simulation of distributed cache nodes.

Expected behavior:

- Prefix key maps to a cache node using consistent hashing.
- Adding/removing a node only remaps a fraction of keys.
- Cache response includes selected node.
- README explains why consistent hashing is useful for horizontal scaling.

Example debug response:

```json
"cache": {
  "hit": false,
  "node": "cache-node-1",
  "key": "iph"
}
```

### 5. Trending searches

Endpoint:

```text
GET /trending
```

Returns top recent searches:

```json
{
  "trending": [
    { "query": "iphone 15", "recent_count": 42 },
    { "query": "ai tools", "recent_count": 31 }
  ]
}
```

Simple implementation:

- Store `recent_count` in SQLite.
- Increment via batch writer.
- Return top N ordered by `recent_count`.

HLD discussion:

- In production, trending could use sliding windows, Redis Sorted Sets, Kafka streams, Flink/Spark jobs, or time-bucketed counters.

### 6. Batch writes

Search endpoint:

```text
POST /search
```

Body:

```json
{ "query": "iphone 15" }
```

Behavior:

- API returns quickly.
- Query increment is stored in an in-memory buffer.
- Background batch writer flushes aggregated counts every few seconds or after buffer threshold.
- DB writes are reduced because repeated queries become one aggregated update.

Metrics should show:

- raw search events received,
- DB write operations performed,
- estimated writes saved,
- flush count,
- pending buffer size.

Crash trade-off:

- In-memory buffer may lose unflushed updates if the app crashes.
- Production solution: durable queue such as Kafka/SQS/RabbitMQ, WAL, Redis Streams, or local append-only log.

### 7. Metrics / observability

Endpoint:

```text
GET /metrics
```

Show:

- total suggest requests,
- cache hits,
- cache misses,
- cache hit rate,
- average suggestion latency,
- total search events,
- DB writes,
- writes saved by batching,
- batch flush count,
- cache node distribution.

## Recommended deliverable

A judge-friendly project with:

- runnable backend,
- simple frontend/demo,
- SQLite data seed,
- consistent hashing cache implementation,
- trending endpoint,
- batch writer implementation,
- metrics endpoint,
- README explaining HLD and trade-offs,
- optional architecture diagram.

## Suggested tech stack

Use the simplest stack that runs locally:

- Python + FastAPI or Flask
- SQLite
- Vanilla HTML/CSS/JS frontend
- No external services required

FastAPI is recommended because it makes API docs easy via `/docs`.
