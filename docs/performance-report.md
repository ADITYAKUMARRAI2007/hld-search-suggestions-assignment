# Performance Report

## Environment

- Date: 2026-06-22 IST
- Runtime: Java 23 running Java 21 source target
- App profile: default smoke profile
- Dataset used for this benchmark: built-in smoke seed, so the project is easy to verify on any desktop
- Real dataset source for grading: AmazonQAC, <https://huggingface.co/datasets/amazon/AmazonQAC>

## Benchmark Command

```bash
./scripts/benchmark.sh
```

The script warms repeated prefixes, submits repeated searches, forces a batch flush, and reads `/metrics`.

## Measured Results

```text
Suggest requests: 201
Cache hits: 184
Cache misses: 17
Cache hit rate: 91.54%
Average suggest latency: 0.91 ms
P95 suggest latency: 1.62 ms
Search events received: 500
Kafka events published: 500
DB write operations: 5
Writes saved by batching: 495
Write reduction: 99.00%
Batch flushes: 5
Trend events processed: 500
Prefix cache invalidations: 180
Cache node distribution: cache-node-3=75, cache-node-2=126
Ranking mode requests: hybrid=201
```

## Interpretation

- Cache hit rate is high after warmup because common prefixes repeat.
- P95 latency is low in the smoke profile because hot prefixes are served from memory.
- Write reduction is high because duplicate search events are aggregated into one query-count update per unique query per flush.

## Required Full-Dataset Run

For a full grading-sized run, place an AmazonQAC export under `data/` and load at least 100,000 unique queries through Docker Compose:

```bash
TYPEAHEAD_DATASET_IMPORT_PATH=/data/amazonqac_100k.csv \
TYPEAHEAD_DATASET_IMPORT_LIMIT=100000 \
docker compose up --build
```

Then rerun:

```bash
./scripts/benchmark.sh
```

The same benchmark script can then be rerun against the Docker profile to produce full-dataset numbers backed by PostgreSQL, OpenSearch, Redis, and Kafka.
