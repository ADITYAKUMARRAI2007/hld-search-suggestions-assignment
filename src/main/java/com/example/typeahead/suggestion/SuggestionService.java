package com.example.typeahead.suggestion;

import com.example.typeahead.cache.CacheKeyService;
import com.example.typeahead.cache.CacheLookup;
import com.example.typeahead.cache.CacheProperties;
import com.example.typeahead.cache.PrefixCache;
import com.example.typeahead.metrics.TypeaheadMetrics;
import com.example.typeahead.model.CacheInfo;
import com.example.typeahead.model.SuggestResponse;
import com.example.typeahead.model.Suggestion;
import com.example.typeahead.normalize.QueryNormalizer;
import com.example.typeahead.ranking.RankMode;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SuggestionService {
    private static final int LIMIT = 10;

    private final QueryNormalizer normalizer;
    private final CacheKeyService cacheKeyService;
    private final PrefixCache prefixCache;
    private final CacheProperties cacheProperties;
    private final SuggestionIndex suggestionIndex;
    private final TypeaheadMetrics metrics;

    public SuggestionService(
            QueryNormalizer normalizer,
            CacheKeyService cacheKeyService,
            PrefixCache prefixCache,
            CacheProperties cacheProperties,
            SuggestionIndex suggestionIndex,
            TypeaheadMetrics metrics) {
        this.normalizer = normalizer;
        this.cacheKeyService = cacheKeyService;
        this.prefixCache = prefixCache;
        this.cacheProperties = cacheProperties;
        this.suggestionIndex = suggestionIndex;
        this.metrics = metrics;
    }

    public SuggestResponse suggest(String rawPrefix, String rawRank, boolean debug) {
        long start = System.nanoTime();
        String normalizedPrefix = normalizer.normalize(rawPrefix);
        RankMode rankMode = RankMode.from(rawRank);
        String cacheKey = cacheKeyService.key(rankMode, normalizedPrefix);

        if (normalizedPrefix.isBlank()) {
            return new SuggestResponse(
                    normalizedPrefix,
                    rankMode.wireName(),
                    List.of(),
                    new CacheInfo(false, "", cacheKey, 0),
                    elapsedMs(start));
        }

        CacheLookup lookup = prefixCache.get(cacheKey);
        if (lookup.hit()) {
            double latencyMs = elapsedMs(start);
            metrics.recordSuggest(latencyMs, true, lookup.node(), rankMode);
            return new SuggestResponse(
                    normalizedPrefix,
                    rankMode.wireName(),
                    lookup.suggestions(),
                    new CacheInfo(true, lookup.node(), lookup.key(), lookup.ttlSecondsRemaining()),
                    latencyMs);
        }

        List<Suggestion> suggestions = suggestionIndex.search(normalizedPrefix, rankMode, LIMIT, debug);
        Duration ttl = cacheProperties.ttlFor(rankMode.wireName());
        prefixCache.put(cacheKey, suggestions, ttl);
        CacheLookup afterPut = prefixCache.get(cacheKey);
        double latencyMs = elapsedMs(start);
        metrics.recordSuggest(latencyMs, false, lookup.node(), rankMode);
        return new SuggestResponse(
                normalizedPrefix,
                rankMode.wireName(),
                suggestions,
                new CacheInfo(false, lookup.node(), cacheKey, afterPut.ttlSecondsRemaining()),
                latencyMs);
    }

    private static double elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000d;
    }
}
