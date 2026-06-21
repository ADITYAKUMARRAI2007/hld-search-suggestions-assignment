package com.example.typeahead.store;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "typeahead.store", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryQueryStore implements QueryStore {
    private final ConcurrentMap<String, MutableQuery> queries = new ConcurrentHashMap<>();

    @Override
    public QueryRecord upsertImported(String normalizedQuery, String displayQuery, long historicalCount, Instant searchedAt) {
        MutableQuery query = queries.compute(normalizedQuery, (key, existing) -> {
            if (existing == null) {
                return new MutableQuery(normalizedQuery, displayQuery, historicalCount, searchedAt);
            }
            existing.displayQuery = displayQuery;
            existing.historicalCount = Math.max(existing.historicalCount, historicalCount);
            existing.lastSearchedAt = latest(existing.lastSearchedAt, searchedAt);
            return existing;
        });
        return query.toRecord();
    }

    @Override
    public List<QueryRecord> incrementCounts(Collection<QueryAggregate> updates) {
        List<QueryRecord> changed = new ArrayList<>();
        for (QueryAggregate update : updates) {
            MutableQuery query = queries.compute(update.normalizedQuery(), (key, existing) -> {
                if (existing == null) {
                    return new MutableQuery(
                            update.normalizedQuery(),
                            update.displayQuery(),
                            update.increment(),
                            update.occurredAt());
                }
                existing.displayQuery = update.displayQuery();
                existing.historicalCount += update.increment();
                existing.lastSearchedAt = latest(existing.lastSearchedAt, update.occurredAt());
                return existing;
            });
            changed.add(query.toRecord());
        }
        return changed;
    }

    @Override
    public List<QueryRecord> findAll() {
        return queries.values()
                .stream()
                .map(MutableQuery::toRecord)
                .sorted(Comparator.comparing(QueryRecord::normalizedQuery))
                .toList();
    }

    @Override
    public Optional<QueryRecord> find(String normalizedQuery) {
        MutableQuery query = queries.get(normalizedQuery);
        return query == null ? Optional.empty() : Optional.of(query.toRecord());
    }

    @Override
    public long count() {
        return queries.size();
    }

    private static Instant latest(Instant current, Instant next) {
        if (current == null) {
            return next;
        }
        if (next == null) {
            return current;
        }
        return current.isAfter(next) ? current : next;
    }

    private static final class MutableQuery {
        private final String normalizedQuery;
        private String displayQuery;
        private long historicalCount;
        private Instant lastSearchedAt;

        private MutableQuery(String normalizedQuery, String displayQuery, long historicalCount, Instant lastSearchedAt) {
            this.normalizedQuery = normalizedQuery;
            this.displayQuery = displayQuery;
            this.historicalCount = historicalCount;
            this.lastSearchedAt = lastSearchedAt;
        }

        private QueryRecord toRecord() {
            return new QueryRecord(normalizedQuery, displayQuery, historicalCount, lastSearchedAt);
        }
    }
}
