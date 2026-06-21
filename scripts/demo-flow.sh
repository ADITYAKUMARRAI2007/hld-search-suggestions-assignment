#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"

echo "1. Historical suggestions"
curl -fsS "$BASE_URL/suggest?q=low&rank=count" && echo

echo "2. Trending-aware suggestions before search burst"
curl -fsS "$BASE_URL/suggest?q=low&rank=hybrid&debug=true" && echo

echo "3. Submit repeated searches"
for i in $(seq 1 40); do
  curl -fsS -X POST "$BASE_URL/search" \
    -H 'Content-Type: application/json' \
    -d '{"query":"low latency search"}' >/dev/null
done

echo "4. Force batch flush"
curl -fsS -X POST "$BASE_URL/api/v1/admin/flush" && echo

echo "5. Trending"
curl -fsS "$BASE_URL/trending?window=1h" && echo

echo "6. Trending-aware suggestions after search burst"
curl -fsS "$BASE_URL/suggest?q=low&rank=hybrid&debug=true" && echo

echo "7. Metrics"
curl -fsS "$BASE_URL/metrics" && echo
