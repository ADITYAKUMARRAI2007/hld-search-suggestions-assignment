# API Documentation

Swagger UI is available at `/docs` when the app is running.

## `GET /suggest`

Aliases:

- `/suggest`
- `/api/v1/suggest`

Parameters:

- `q`: typed prefix
- `rank`: `count` or `hybrid`, default `hybrid`
- `debug`: includes score breakdown when `true`

Example:

```bash
curl 'http://127.0.0.1:8080/suggest?q=low&rank=hybrid&debug=true'
```

Response fields:

- `suggestions`: max 10 prefix matches
- `cache.hit`: cache hit/miss
- `cache.node`: selected consistent-hash cache node
- `latency_ms`: API latency

## `POST /search`

Aliases:

- `/search`
- `/api/v1/search`

```bash
curl -X POST 'http://127.0.0.1:8080/search' \
  -H 'Content-Type: application/json' \
  -d '{"query":"low latency search"}'
```

Returns quickly with `message=Searched` and queues the count update for batch processing.

## `GET /trending`

Aliases:

- `/trending`
- `/api/v1/trending`

Parameters:

- `window`: `1h`, `24h`, or `7d`
- `limit`: default `10`

## `GET /cache/debug`

Aliases:

- `/cache/debug`
- `/api/v1/cache/debug`

Shows normalized prefix, cache key, selected cache node, hit/miss, TTL, and hash-ring settings.

## `GET /metrics`

Aliases:

- `/metrics`
- `/api/v1/metrics/summary`

Returns assignment-friendly metrics: latency, p95, cache hit rate, search events, DB writes, writes saved, write reduction, batch flushes, pending buffer size, trend events, invalidations, cache node distribution, and ranking-mode requests.
