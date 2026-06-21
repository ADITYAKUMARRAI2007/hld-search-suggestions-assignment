package com.example.typeahead.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.typeahead.batch.BatchAggregator;
import com.example.typeahead.batch.BatchProperties;
import com.example.typeahead.cache.CacheKeyService;
import com.example.typeahead.cache.CacheProperties;
import com.example.typeahead.cache.InMemoryPrefixCache;
import com.example.typeahead.metrics.TypeaheadMetrics;
import com.example.typeahead.model.MetricsSummary;
import com.example.typeahead.model.SearchRequest;
import com.example.typeahead.normalize.QueryNormalizer;
import com.example.typeahead.ranking.RankingService;
import com.example.typeahead.search.InMemorySearchEventPublisher;
import com.example.typeahead.search.SearchSubmissionService;
import com.example.typeahead.store.InMemoryQueryStore;
import com.example.typeahead.suggestion.InMemorySuggestionIndex;
import com.example.typeahead.suggestion.SuggestionService;
import com.example.typeahead.trending.TrendBucketService;
import com.example.typeahead.trending.TrendWindow;
import com.example.typeahead.trending.TrendingProperties;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class BatchTrendingFlowTest {
    @Test
    void repeatedSearchesLiftQueryInHybridRankingAndReduceWrites() {
        TestRig rig = new TestRig();
        rig.store.upsertImported("low cost flights", "low cost flights", 190_000, Instant.now());
        rig.store.upsertImported("low latency search", "low latency search", 20_000, Instant.now());

        assertThat(rig.suggestionService.suggest("low", "count", true).suggestions().getFirst().query())
                .isEqualTo("low cost flights");

        for (int i = 0; i < 40; i++) {
            rig.searchSubmissionService.submit(new SearchRequest("low latency search"));
        }
        rig.batchAggregator.flush();

        assertThat(rig.trendBucketService.recentCount("low latency search", TrendWindow.ONE_HOUR))
                .isEqualTo(40);
        assertThat(rig.suggestionService.suggest("low", "hybrid", true).suggestions().getFirst().query())
                .isEqualTo("low latency search");

        MetricsSummary metrics = rig.metrics.snapshot(rig.batchAggregator.pendingBufferSize());
        assertThat(metrics.searchEventsReceived()).isEqualTo(40);
        assertThat(metrics.dbWriteOperations()).isEqualTo(1);
        assertThat(metrics.writesSavedByBatching()).isEqualTo(39);
    }

    private static final class TestRig {
        private final QueryNormalizer normalizer = new QueryNormalizer();
        private final RankingService rankingService = new RankingService();
        private final InMemoryQueryStore store = new InMemoryQueryStore();
        private final TrendBucketService trendBucketService = new TrendBucketService(
                new TrendingProperties(5), store, rankingService);
        private final InMemorySuggestionIndex suggestionIndex = new InMemorySuggestionIndex(
                store, trendBucketService, rankingService);
        private final CacheProperties cacheProperties = new CacheProperties(
                1, "in-memory", 128, 20, 180, 15, List.of("cache-node-1", "cache-node-2", "cache-node-3"));
        private final InMemoryPrefixCache prefixCache = new InMemoryPrefixCache(cacheProperties);
        private final CacheKeyService cacheKeyService = new CacheKeyService(cacheProperties);
        private final TypeaheadMetrics metrics = new TypeaheadMetrics();
        private final BatchAggregator batchAggregator = new BatchAggregator(
                new BatchProperties(2_000, 500),
                store,
                trendBucketService,
                suggestionIndex,
                prefixCache,
                cacheKeyService,
                metrics);
        private final InMemorySearchEventPublisher publisher = new InMemorySearchEventPublisher(batchAggregator, metrics);
        private final SearchSubmissionService searchSubmissionService = new SearchSubmissionService(
                normalizer, publisher, metrics);
        private final SuggestionService suggestionService = new SuggestionService(
                normalizer,
                cacheKeyService,
                prefixCache,
                cacheProperties,
                suggestionIndex,
                metrics);
    }
}
