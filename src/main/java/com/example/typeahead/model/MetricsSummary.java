package com.example.typeahead.model;

import java.util.Map;

public record MetricsSummary(
        long suggestRequests,
        long cacheHits,
        long cacheMisses,
        double cacheHitRate,
        double avgSuggestLatencyMs,
        double p95SuggestLatencyMs,
        long searchEventsReceived,
        long kafkaEventsPublished,
        long dbWriteOperations,
        long writesSavedByBatching,
        double writeReductionPercent,
        long batchFlushes,
        long lastFlushUniqueQueries,
        long pendingBufferSize,
        long trendEventsProcessed,
        long prefixCacheInvalidations,
        Map<String, Long> cacheNodeDistribution,
        Map<String, Long> rankingModeRequests) {
}
