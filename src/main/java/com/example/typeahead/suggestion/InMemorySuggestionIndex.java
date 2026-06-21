package com.example.typeahead.suggestion;

import com.example.typeahead.model.Suggestion;
import com.example.typeahead.ranking.RankMode;
import com.example.typeahead.ranking.RankingService;
import com.example.typeahead.store.QueryRecord;
import com.example.typeahead.store.QueryStore;
import com.example.typeahead.trending.TrendBucketService;
import com.example.typeahead.trending.TrendWindow;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "typeahead.search-index", havingValue = "in-memory", matchIfMissing = true)
public class InMemorySuggestionIndex implements SuggestionIndex {
    private final QueryStore queryStore;
    private final TrendBucketService trendBucketService;
    private final RankingService rankingService;

    public InMemorySuggestionIndex(
            QueryStore queryStore,
            TrendBucketService trendBucketService,
            RankingService rankingService) {
        this.queryStore = queryStore;
        this.trendBucketService = trendBucketService;
        this.rankingService = rankingService;
    }

    @Override
    public List<Suggestion> search(String normalizedPrefix, RankMode rankMode, int limit, boolean includeDebug) {
        return queryStore.findAll()
                .stream()
                .filter(record -> record.normalizedQuery().startsWith(normalizedPrefix))
                .map(record -> toSuggestion(record, rankMode, includeDebug))
                .sorted(Comparator.comparingLong(Suggestion::score).reversed().thenComparing(Suggestion::query))
                .limit(limit)
                .toList();
    }

    @Override
    public void refresh(Collection<QueryRecord> records) {
        // In-memory index reads directly from QueryStore, so refresh is immediate.
    }

    private Suggestion toSuggestion(QueryRecord record, RankMode rankMode, boolean includeDebug) {
        long recent1h = trendBucketService.recentCount(record.normalizedQuery(), TrendWindow.ONE_HOUR);
        long recent24h = trendBucketService.recentCount(record.normalizedQuery(), TrendWindow.TWENTY_FOUR_HOURS);
        long score = rankingService.score(rankMode, record.historicalCount(), recent1h, recent24h);
        Map<String, Long> breakdown = includeDebug || rankMode == RankMode.HYBRID
                ? rankingService.scoreBreakdown(record.historicalCount(), recent1h, recent24h)
                : Map.of();
        return new Suggestion(record.displayQuery(), record.historicalCount(), recent1h, recent24h, score, breakdown);
    }
}
