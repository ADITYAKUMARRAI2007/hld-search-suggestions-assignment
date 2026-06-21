CREATE TABLE IF NOT EXISTS query_catalog (
    id BIGSERIAL PRIMARY KEY,
    normalized_query TEXT NOT NULL UNIQUE,
    display_query TEXT NOT NULL,
    historical_count BIGINT NOT NULL DEFAULT 0,
    last_searched_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_query_catalog_normalized_prefix
    ON query_catalog (normalized_query text_pattern_ops);

CREATE INDEX IF NOT EXISTS idx_query_catalog_historical_count
    ON query_catalog (historical_count DESC);

CREATE TABLE IF NOT EXISTS query_trend_buckets (
    bucket_start TIMESTAMPTZ NOT NULL,
    bucket_minutes INT NOT NULL,
    normalized_query TEXT NOT NULL,
    display_query TEXT NOT NULL,
    search_count BIGINT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (bucket_start, bucket_minutes, normalized_query)
);

CREATE INDEX IF NOT EXISTS idx_query_trend_bucket_count
    ON query_trend_buckets (bucket_minutes, bucket_start DESC, search_count DESC);

CREATE TABLE IF NOT EXISTS dataset_import_runs (
    id BIGSERIAL PRIMARY KEY,
    dataset_name TEXT NOT NULL,
    dataset_url TEXT NOT NULL,
    source_format TEXT NOT NULL,
    rows_read BIGINT NOT NULL,
    unique_queries_loaded BIGINT NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ,
    status TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS batch_flush_audit (
    id BIGSERIAL PRIMARY KEY,
    raw_events BIGINT NOT NULL,
    unique_queries BIGINT NOT NULL,
    db_write_operations BIGINT NOT NULL,
    writes_saved BIGINT NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ NOT NULL
);
