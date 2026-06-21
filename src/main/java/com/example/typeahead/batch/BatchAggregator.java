package com.example.typeahead.batch;

import com.example.typeahead.cache.CacheKeyService;
import com.example.typeahead.cache.PrefixCache;
import com.example.typeahead.metrics.TypeaheadMetrics;
import com.example.typeahead.search.SearchEvent;
import com.example.typeahead.store.QueryAggregate;
import com.example.typeahead.store.QueryRecord;
import com.example.typeahead.store.QueryStore;
import com.example.typeahead.suggestion.SuggestionIndex;
import com.example.typeahead.trending.TrendBucketService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BatchAggregator {
    private final BatchProperties properties;
    private final QueryStore queryStore;
    private final TrendBucketService trendBucketService;
    private final SuggestionIndex suggestionIndex;
    private final PrefixCache prefixCache;
    private final CacheKeyService cacheKeyService;
    private final TypeaheadMetrics metrics;
    private final Map<String, PendingAggregate> pending = new LinkedHashMap<>();
    private long pendingEvents;

    public BatchAggregator(
            BatchProperties properties,
            QueryStore queryStore,
            TrendBucketService trendBucketService,
            SuggestionIndex suggestionIndex,
            PrefixCache prefixCache,
            CacheKeyService cacheKeyService,
            TypeaheadMetrics metrics) {
        this.properties = properties;
        this.queryStore = queryStore;
        this.trendBucketService = trendBucketService;
        this.suggestionIndex = suggestionIndex;
        this.prefixCache = prefixCache;
        this.cacheKeyService = cacheKeyService;
        this.metrics = metrics;
    }

    public synchronized void accept(SearchEvent event) {
        pending.merge(
                event.normalizedQuery(),
                new PendingAggregate(event.displayQuery(), 1, event.occurredAt()),
                PendingAggregate::merge);
        pendingEvents++;
        if (pendingEvents >= properties.maxEvents()) {
            flush();
        }
    }

    @Scheduled(fixedDelayString = "${typeahead.batch.flush-interval-ms:2000}")
    public synchronized void scheduledFlush() {
        flush();
    }

    public synchronized BatchFlushResult flush() {
        if (pending.isEmpty()) {
            return new BatchFlushResult(0, 0, 0);
        }
        Instant startedAt = Instant.now();
        long rawEvents = pendingEvents;
        List<QueryAggregate> updates = new ArrayList<>(pending.size());
        pending.forEach((normalizedQuery, aggregate) -> updates.add(new QueryAggregate(
                normalizedQuery,
                aggregate.displayQuery(),
                aggregate.increment(),
                aggregate.latestOccurredAt())));
        pending.clear();
        pendingEvents = 0;

        List<QueryRecord> changedRecords = queryStore.incrementCounts(updates);
        trendBucketService.record(updates);
        suggestionIndex.refresh(changedRecords);
        int invalidations = invalidatePrefixes(changedRecords);
        metrics.recordTrendEventsProcessed(rawEvents);
        metrics.recordPrefixInvalidations(invalidations);
        metrics.recordBatchFlush(rawEvents, updates.size(), Instant.now().toEpochMilli() - startedAt.toEpochMilli());
        return new BatchFlushResult(rawEvents, updates.size(), invalidations);
    }

    public synchronized long pendingBufferSize() {
        return pending.size();
    }

    private int invalidatePrefixes(Collection<QueryRecord> records) {
        List<String> keys = records.stream()
                .flatMap(record -> cacheKeyService.keysForUpdatedQuery(record.normalizedQuery()).stream())
                .toList();
        prefixCache.invalidateKeys(keys);
        return keys.size();
    }

    private record PendingAggregate(String displayQuery, long increment, Instant latestOccurredAt) {
        PendingAggregate merge(PendingAggregate other) {
            Instant latest = latestOccurredAt.isAfter(other.latestOccurredAt) ? latestOccurredAt : other.latestOccurredAt;
            return new PendingAggregate(displayQuery, increment + other.increment, latest);
        }
    }
}
