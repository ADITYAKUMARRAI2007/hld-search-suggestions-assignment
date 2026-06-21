package com.example.typeahead.metrics;

import com.example.typeahead.model.MetricsSummary;
import com.example.typeahead.ranking.RankMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class TypeaheadMetrics {
    private static final int MAX_LATENCY_SAMPLES = 5_000;

    private final AtomicLong suggestRequests = new AtomicLong();
    private final AtomicLong cacheHits = new AtomicLong();
    private final AtomicLong cacheMisses = new AtomicLong();
    private final AtomicLong searchEventsReceived = new AtomicLong();
    private final AtomicLong kafkaEventsPublished = new AtomicLong();
    private final AtomicLong dbWriteOperations = new AtomicLong();
    private final AtomicLong writesSavedByBatching = new AtomicLong();
    private final AtomicLong batchFlushes = new AtomicLong();
    private final AtomicLong lastFlushUniqueQueries = new AtomicLong();
    private final AtomicLong trendEventsProcessed = new AtomicLong();
    private final AtomicLong prefixCacheInvalidations = new AtomicLong();
    private final ConcurrentLinkedDeque<Double> suggestLatencyMs = new ConcurrentLinkedDeque<>();
    private final Map<String, AtomicLong> cacheNodeDistribution = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> rankingModeRequests = new ConcurrentHashMap<>();

    public void recordSuggest(double latencyMs, boolean cacheHit, String cacheNode, RankMode rankMode) {
        suggestRequests.incrementAndGet();
        if (cacheHit) {
            cacheHits.incrementAndGet();
        } else {
            cacheMisses.incrementAndGet();
        }
        suggestLatencyMs.add(latencyMs);
        while (suggestLatencyMs.size() > MAX_LATENCY_SAMPLES) {
            suggestLatencyMs.poll();
        }
        cacheNodeDistribution.computeIfAbsent(cacheNode, ignored -> new AtomicLong()).incrementAndGet();
        rankingModeRequests.computeIfAbsent(rankMode.wireName(), ignored -> new AtomicLong()).incrementAndGet();
    }

    public void recordSearchEventReceived() {
        searchEventsReceived.incrementAndGet();
    }

    public void recordKafkaEventPublished() {
        kafkaEventsPublished.incrementAndGet();
    }

    public void recordTrendEventsProcessed(long count) {
        trendEventsProcessed.addAndGet(count);
    }

    public void recordPrefixInvalidations(long count) {
        prefixCacheInvalidations.addAndGet(count);
    }

    public void recordBatchFlush(long rawEvents, long uniqueQueries, long durationMs) {
        batchFlushes.incrementAndGet();
        lastFlushUniqueQueries.set(uniqueQueries);
        dbWriteOperations.addAndGet(uniqueQueries);
        writesSavedByBatching.addAndGet(Math.max(0, rawEvents - uniqueQueries));
    }

    public MetricsSummary snapshot(long pendingBufferSize) {
        long requests = suggestRequests.get();
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        long dbWrites = dbWriteOperations.get();
        long saved = writesSavedByBatching.get();
        long totalWriteCandidates = dbWrites + saved;
        return new MetricsSummary(
                requests,
                hits,
                misses,
                requests == 0 ? 0d : hits / (double) requests,
                averageLatency(),
                p95Latency(),
                searchEventsReceived.get(),
                kafkaEventsPublished.get(),
                dbWrites,
                saved,
                totalWriteCandidates == 0 ? 0d : saved * 100d / totalWriteCandidates,
                batchFlushes.get(),
                lastFlushUniqueQueries.get(),
                pendingBufferSize,
                trendEventsProcessed.get(),
                prefixCacheInvalidations.get(),
                toLongMap(cacheNodeDistribution),
                toLongMap(rankingModeRequests));
    }

    private double averageLatency() {
        if (suggestLatencyMs.isEmpty()) {
            return 0d;
        }
        return suggestLatencyMs.stream().mapToDouble(Double::doubleValue).average().orElse(0d);
    }

    private double p95Latency() {
        if (suggestLatencyMs.isEmpty()) {
            return 0d;
        }
        ArrayList<Double> sorted = new ArrayList<>(suggestLatencyMs);
        sorted.sort(Comparator.naturalOrder());
        int index = (int) Math.ceil(sorted.size() * 0.95d) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    private static Map<String, Long> toLongMap(Map<String, AtomicLong> source) {
        return source.entrySet()
                .stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().get()));
    }
}
