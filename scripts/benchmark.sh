#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
PREFIXES=("sha" "sta" "aqu" "ute" "blu" "sku" "nai" "dec")

for i in $(seq 1 200); do
  prefix="${PREFIXES[$((i % ${#PREFIXES[@]}))]}"
  curl -fsS "$BASE_URL/suggest?q=$prefix&rank=hybrid" >/dev/null
done

for i in $(seq 1 500); do
  curl -fsS -X POST "$BASE_URL/search" \
    -H 'Content-Type: application/json' \
    -d '{"query":"low latency search"}' >/dev/null
done

curl -fsS -X POST "$BASE_URL/api/v1/admin/flush" >/dev/null
curl -fsS "$BASE_URL/metrics"
