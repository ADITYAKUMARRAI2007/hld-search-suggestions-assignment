package com.example.typeahead.trending;

import com.example.typeahead.store.QueryAggregate;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "typeahead.store", havingValue = "jdbc")
public class JdbcTrendBucketStore implements TrendBucketStore {
    private final JdbcTemplate jdbcTemplate;

    public JdbcTrendBucketStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void record(Collection<QueryAggregate> aggregates, int bucketMinutes) {
        for (QueryAggregate aggregate : aggregates) {
            jdbcTemplate.update("""
                    INSERT INTO query_trend_buckets(bucket_start, bucket_minutes, normalized_query, display_query, search_count)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT (bucket_start, bucket_minutes, normalized_query)
                    DO UPDATE SET
                      display_query = EXCLUDED.display_query,
                      search_count = query_trend_buckets.search_count + EXCLUDED.search_count,
                      updated_at = now()
                    """,
                    Timestamp.from(bucketStart(aggregate.occurredAt(), bucketMinutes)),
                    bucketMinutes,
                    aggregate.normalizedQuery(),
                    aggregate.displayQuery(),
                    aggregate.increment());
        }
    }

    @Override
    public long recentCount(String normalizedQuery, TrendWindow window, int bucketMinutes) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(search_count), 0)
                FROM query_trend_buckets
                WHERE normalized_query = ?
                  AND bucket_minutes = ?
                  AND bucket_start >= ?
                """,
                Long.class,
                normalizedQuery,
                bucketMinutes,
                Timestamp.from(Instant.now().minus(window.duration())));
        return count == null ? 0 : count;
    }

    @Override
    public List<TrendAggregate> totals(Duration window, int bucketMinutes) {
        return jdbcTemplate.query("""
                SELECT normalized_query, MAX(display_query) AS display_query, SUM(search_count) AS total_count
                FROM query_trend_buckets
                WHERE bucket_minutes = ?
                  AND bucket_start >= ?
                GROUP BY normalized_query
                """,
                (rs, rowNum) -> new TrendAggregate(
                        rs.getString("normalized_query"),
                        rs.getString("display_query"),
                        rs.getLong("total_count")),
                bucketMinutes,
                Timestamp.from(Instant.now().minus(window)));
    }

    private static Instant bucketStart(Instant instant, int bucketMinutes) {
        Instant value = instant == null ? Instant.now() : instant;
        long bucketSeconds = Duration.ofMinutes(bucketMinutes).toSeconds();
        return Instant.ofEpochSecond((value.getEpochSecond() / bucketSeconds) * bucketSeconds);
    }
}
