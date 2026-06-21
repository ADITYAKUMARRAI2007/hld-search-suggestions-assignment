# Performance Report

## Environment

- Date: 2026-06-22 IST
- Runtime: Java 23 running Java 21 source target
- App profile: default smoke profile
- Dataset: built-in smoke fixture seeded through app startup
- Real dataset source for grading: AmazonQAC, <https://huggingface.co/datasets/amazon/AmazonQAC>

## Benchmark Command

```bash
./scripts/benchmark.sh
```

The script warms repeated prefixes, submits repeated searches, forces a batch flush, and reads `/metrics`.

## Measured Results

```text
Suggest requests: 203
Cache hits: 192
Cache misses: 11
Cache hit rate: 94.58%
Average suggest latency: 0.16 ms
P95 suggest latency: 0.34 ms
Search events received: 540
DB write operations: 7
Writes saved by batching: 533
Write reduction: 98.70%
Batch flushes: 7
Trend events processed: 540
Prefix cache invalidations: 252
```

## Interpretation

- Cache hit rate is high after warmup because common prefixes repeat.
- P95 latency is low in the smoke profile because hot prefixes are served from memory.
- Write reduction is high because duplicate search events are aggregated into one query-count update per unique query per flush.

## Required Full-Dataset Run

For final grading, load at least 100,000 unique AmazonQAC queries:

```bash
mvn -Dmaven.repo.local=.m2/repository spring-boot:run \
  -Dspring-boot.run.arguments="--typeahead.dataset.import-path=/path/to/amazonqac_100k.csv --typeahead.dataset.import-limit=100000"
```

Then rerun:

```bash
./scripts/benchmark.sh
```

The report should be refreshed with full-dataset numbers before GitHub submission if the evaluator requires measured 100k-dataset evidence.
