# Demo Script

Run the app:

```bash
mvn -Dmaven.repo.local=.m2/repository spring-boot:run
```

Run the scripted API demo:

```bash
./scripts/demo-flow.sh
```

Manual walkthrough:

1. Open `http://127.0.0.1:8080`.
2. Search for `low`.
3. Select `Overall` and observe `low cost flights` ranks first by historical count.
4. Select `Trending-aware` after repeated searches and observe `low latency search` ranks first.
5. Check Cache Debug for hit/miss, node, cache key, TTL, and latency.
6. Check Metrics for cache hit rate and write reduction.
7. Open `/docs` for Swagger/OpenAPI.

Screenshots:

- `docs/screenshots/search-ui-suggestions.png`
- `docs/screenshots/search-ui-overall-mode.png`
