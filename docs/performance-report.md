# Performance Report

## Environment

- Date: 2026-06-22 IST
- Runtime: Docker Compose on Colima, Java 21 container runtime
- App profile: Docker profile with PostgreSQL, OpenSearch, Redis shards, and Kafka
- Dataset source: AmazonQAC, <https://huggingface.co/datasets/amazon/AmazonQAC>
- Loaded dataset size: 100,000 unique real queries
- PostgreSQL verification: `query_catalog = 100000`
- OpenSearch verification: `query_suggestions_v1 = 100000`

## Benchmark Command

```bash
./scripts/benchmark.sh
```

The script warms repeated prefixes, submits repeated searches, forces a batch flush, and reads `/metrics`.

## Measured Results

```text
Suggest requests: 200
Cache hits: 192
Cache misses: 8
Cache hit rate: 96.00%
Average suggest latency: 6.40 ms
P95 suggest latency: 6.43 ms
Search events received: 500
Kafka events published: 500
DB write operations: 8
Writes saved by batching: 492
Write reduction: 98.40%
Batch flushes: 8
Trend events processed: 500
Prefix cache invalidations: 288
Cache node distribution: cache-node-1=75, cache-node-2=100, cache-node-3=25
Ranking mode requests: hybrid=200
```

## Interpretation

- The benchmark uses AmazonQAC prefixes present in the loaded 100,000-query export.
- Cache hit rate is high after the first request per repeated prefix because Redis stores prefix result sets.
- P95 latency remains low because hot suggestions are served from Redis and cache misses use the OpenSearch completion index.
- Write reduction is high because 500 repeated search submissions are aggregated before PostgreSQL/OpenSearch updates.
- The database/index gates above prove this was measured after loading the required 100,000-query dataset, not the small local fixture.
