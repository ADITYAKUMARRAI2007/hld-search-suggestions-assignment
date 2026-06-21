package com.example.typeahead.trending;

import com.example.typeahead.model.TrendingItem;
import com.example.typeahead.ranking.RankingService;
import com.example.typeahead.store.QueryAggregate;
import com.example.typeahead.store.QueryRecord;
import com.example.typeahead.store.QueryStore;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class TrendBucketService {
    private final int bucketMinutes;
    private final TrendBucketStore trendBucketStore;
    private final QueryStore queryStore;
    private final RankingService rankingService;

    public TrendBucketService(
            TrendingProperties properties,
            TrendBucketStore trendBucketStore,
            QueryStore queryStore,
            RankingService rankingService) {
        this.bucketMinutes = properties.bucketMinutes();
        this.trendBucketStore = trendBucketStore;
        this.queryStore = queryStore;
        this.rankingService = rankingService;
    }

    public void record(Collection<QueryAggregate> aggregates) {
        trendBucketStore.record(aggregates, bucketMinutes);
    }

    public long recentCount(String normalizedQuery, TrendWindow window) {
        return trendBucketStore.recentCount(normalizedQuery, window, bucketMinutes);
    }

    public List<TrendingItem> top(TrendWindow window, int limit) {
        return trendBucketStore.totals(window.duration(), bucketMinutes)
                .stream()
                .map(aggregate -> toTrendingItem(aggregate, window))
                .sorted(Comparator.comparingLong(TrendingItem::score).reversed().thenComparing(TrendingItem::query))
                .limit(Math.max(1, Math.min(limit, 50)))
                .toList();
    }

    public Map<String, Long> recentCounts(String normalizedQuery) {
        return Map.of(
                "1h", recentCount(normalizedQuery, TrendWindow.ONE_HOUR),
                "24h", recentCount(normalizedQuery, TrendWindow.TWENTY_FOUR_HOURS));
    }

    private TrendingItem toTrendingItem(TrendAggregate aggregate, TrendWindow window) {
        QueryRecord record = queryStore.find(aggregate.normalizedQuery())
                .orElse(new QueryRecord(aggregate.normalizedQuery(), aggregate.displayQuery(), 0, null));
        long recent = aggregate.count();
        long recent1h = window == TrendWindow.ONE_HOUR
                ? recent
                : recentCount(aggregate.normalizedQuery(), TrendWindow.ONE_HOUR);
        long recent24h = window == TrendWindow.TWENTY_FOUR_HOURS
                ? recent
                : recentCount(aggregate.normalizedQuery(), TrendWindow.TWENTY_FOUR_HOURS);
        long score = rankingService.score(com.example.typeahead.ranking.RankMode.HYBRID, record.historicalCount(), recent1h, recent24h);
        return new TrendingItem(record.displayQuery(), recent, record.historicalCount(), score);
    }
}
