package com.example.typeahead.trending;

import com.example.typeahead.store.QueryAggregate;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "typeahead.store", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryTrendBucketStore implements TrendBucketStore {
    private final ConcurrentMap<Instant, ConcurrentMap<String, TrendCounter>> buckets = new ConcurrentHashMap<>();

    @Override
    public void record(Collection<QueryAggregate> aggregates, int bucketMinutes) {
        for (QueryAggregate aggregate : aggregates) {
            Instant bucket = bucketStart(aggregate.occurredAt(), bucketMinutes);
            buckets.computeIfAbsent(bucket, ignored -> new ConcurrentHashMap<>())
                    .merge(
                            aggregate.normalizedQuery(),
                            new TrendCounter(aggregate.displayQuery(), aggregate.increment()),
                            TrendCounter::merge);
        }
    }

    @Override
    public long recentCount(String normalizedQuery, TrendWindow window, int bucketMinutes) {
        Instant cutoff = Instant.now().minus(window.duration());
        return buckets.entrySet()
                .stream()
                .filter(entry -> !entry.getKey().isBefore(cutoff))
                .mapToLong(entry -> {
                    TrendCounter counter = entry.getValue().get(normalizedQuery);
                    return counter == null ? 0 : counter.count();
                })
                .sum();
    }

    @Override
    public List<TrendAggregate> totals(Duration window, int bucketMinutes) {
        Instant cutoff = Instant.now().minus(window);
        Map<String, TrendCounter> totals = new HashMap<>();
        buckets.entrySet()
                .stream()
                .filter(entry -> !entry.getKey().isBefore(cutoff))
                .forEach(entry -> entry.getValue().forEach((query, counter) ->
                        totals.merge(query, counter, TrendCounter::merge)));
        return totals.entrySet()
                .stream()
                .map(entry -> new TrendAggregate(entry.getKey(), entry.getValue().displayQuery(), entry.getValue().count()))
                .toList();
    }

    private static Instant bucketStart(Instant instant, int bucketMinutes) {
        Instant value = instant == null ? Instant.now() : instant;
        long bucketSeconds = Duration.ofMinutes(bucketMinutes).toSeconds();
        return Instant.ofEpochSecond((value.getEpochSecond() / bucketSeconds) * bucketSeconds);
    }

    private record TrendCounter(String displayQuery, long count) {
        TrendCounter merge(TrendCounter other) {
            return new TrendCounter(displayQuery, count + other.count);
        }
    }
}
