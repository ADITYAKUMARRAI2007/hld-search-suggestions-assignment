package com.example.typeahead.store;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "typeahead.store", havingValue = "jdbc")
public class JdbcQueryStore implements QueryStore {
    private final JdbcTemplate jdbcTemplate;

    public JdbcQueryStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public QueryRecord upsertImported(String normalizedQuery, String displayQuery, long historicalCount, Instant searchedAt) {
        jdbcTemplate.update("""
                INSERT INTO query_catalog(normalized_query, display_query, historical_count, last_searched_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (normalized_query)
                DO UPDATE SET
                  display_query = EXCLUDED.display_query,
                  historical_count = GREATEST(query_catalog.historical_count, EXCLUDED.historical_count),
                  last_searched_at = GREATEST(query_catalog.last_searched_at, EXCLUDED.last_searched_at),
                  updated_at = now()
                """, normalizedQuery, displayQuery, historicalCount, toTimestamp(searchedAt));
        return find(normalizedQuery).orElseThrow();
    }

    @Override
    public List<QueryRecord> incrementCounts(Collection<QueryAggregate> updates) {
        for (QueryAggregate update : updates) {
            jdbcTemplate.update("""
                    INSERT INTO query_catalog(normalized_query, display_query, historical_count, last_searched_at)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT (normalized_query)
                    DO UPDATE SET
                      display_query = EXCLUDED.display_query,
                      historical_count = query_catalog.historical_count + EXCLUDED.historical_count,
                      last_searched_at = GREATEST(query_catalog.last_searched_at, EXCLUDED.last_searched_at),
                      updated_at = now()
                    """, update.normalizedQuery(), update.displayQuery(), update.increment(), toTimestamp(update.occurredAt()));
        }
        return updates.stream()
                .map(QueryAggregate::normalizedQuery)
                .distinct()
                .flatMap(query -> find(query).stream())
                .toList();
    }

    @Override
    public List<QueryRecord> findAll() {
        return jdbcTemplate.query("""
                SELECT normalized_query, display_query, historical_count, last_searched_at
                FROM query_catalog
                """, (rs, rowNum) -> new QueryRecord(
                rs.getString("normalized_query"),
                rs.getString("display_query"),
                rs.getLong("historical_count"),
                toInstant(rs.getTimestamp("last_searched_at"))));
    }

    @Override
    public Optional<QueryRecord> find(String normalizedQuery) {
        List<QueryRecord> rows = jdbcTemplate.query("""
                SELECT normalized_query, display_query, historical_count, last_searched_at
                FROM query_catalog
                WHERE normalized_query = ?
                """, (rs, rowNum) -> new QueryRecord(
                rs.getString("normalized_query"),
                rs.getString("display_query"),
                rs.getLong("historical_count"),
                toInstant(rs.getTimestamp("last_searched_at"))), normalizedQuery);
        return rows.stream().findFirst();
    }

    @Override
    public long count() {
        Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM query_catalog", Long.class);
        return count == null ? 0 : count;
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
