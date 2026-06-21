package com.example.typeahead.trending;

import com.example.typeahead.model.TrendingItem;
import com.example.typeahead.ranking.RankingService;
import com.example.typeahead.store.QueryAggregate;
import com.example.typeahead.store.QueryRecord;
import com.example.typeahead.store.QueryStore;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

@Component
public class TrendBucketService {
    private final int bucketMinutes;
    private final ConcurrentMap<Instant, ConcurrentMap<String, TrendCounter>> buckets = new ConcurrentHashMap<>();
    private final QueryStore queryStore;
    private final RankingService rankingService;

    public TrendBucketService(TrendingProperties properties, QueryStore queryStore, RankingService rankingService) {
        this.bucketMinutes = properties.bucketMinutes();
        this.queryStore = queryStore;
        this.rankingService = rankingService;
    }

    public void record(Collection<QueryAggregate> aggregates) {
        for (QueryAggregate aggregate : aggregates) {
            Instant bucket = bucketStart(aggregate.occurredAt());
            buckets.computeIfAbsent(bucket, ignored -> new ConcurrentHashMap<>())
                    .merge(
                            aggregate.normalizedQuery(),
                            new TrendCounter(aggregate.displayQuery(), aggregate.increment()),
                            TrendCounter::merge);
        }
    }

    public long recentCount(String normalizedQuery, TrendWindow window) {
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

    public List<TrendingItem> top(TrendWindow window, int limit) {
        Map<String, TrendCounter> totals = totals(window.duration());
        return totals.entrySet()
                .stream()
                .map(entry -> toTrendingItem(entry.getKey(), entry.getValue(), window))
                .sorted(Comparator.comparingLong(TrendingItem::score).reversed().thenComparing(TrendingItem::query))
                .limit(Math.max(1, Math.min(limit, 50)))
                .toList();
    }

    public Map<String, Long> recentCounts(String normalizedQuery) {
        return Map.of(
                "1h", recentCount(normalizedQuery, TrendWindow.ONE_HOUR),
                "24h", recentCount(normalizedQuery, TrendWindow.TWENTY_FOUR_HOURS));
    }

    private TrendingItem toTrendingItem(String normalizedQuery, TrendCounter counter, TrendWindow window) {
        QueryRecord record = queryStore.find(normalizedQuery)
                .orElse(new QueryRecord(normalizedQuery, counter.displayQuery(), 0, null));
        long recent = counter.count();
        long recent1h = window == TrendWindow.ONE_HOUR ? recent : recentCount(normalizedQuery, TrendWindow.ONE_HOUR);
        long recent24h = window == TrendWindow.TWENTY_FOUR_HOURS ? recent : recentCount(normalizedQuery, TrendWindow.TWENTY_FOUR_HOURS);
        long score = rankingService.score(com.example.typeahead.ranking.RankMode.HYBRID, record.historicalCount(), recent1h, recent24h);
        return new TrendingItem(record.displayQuery(), recent, record.historicalCount(), score);
    }

    private Map<String, TrendCounter> totals(Duration window) {
        Instant cutoff = Instant.now().minus(window);
        Map<String, TrendCounter> totals = new HashMap<>();
        buckets.entrySet()
                .stream()
                .filter(entry -> !entry.getKey().isBefore(cutoff))
                .forEach(entry -> entry.getValue().forEach((query, counter) ->
                        totals.merge(query, counter, TrendCounter::merge)));
        return totals;
    }

    private Instant bucketStart(Instant instant) {
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
