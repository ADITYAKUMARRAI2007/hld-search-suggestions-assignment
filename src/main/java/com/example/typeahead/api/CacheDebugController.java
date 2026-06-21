package com.example.typeahead.api;

import com.example.typeahead.cache.CacheKeyService;
import com.example.typeahead.cache.PrefixCache;
import com.example.typeahead.model.CacheDebugResponse;
import com.example.typeahead.normalize.QueryNormalizer;
import com.example.typeahead.ranking.RankMode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CacheDebugController {
    private final QueryNormalizer normalizer;
    private final CacheKeyService cacheKeyService;
    private final PrefixCache prefixCache;

    public CacheDebugController(
            QueryNormalizer normalizer,
            CacheKeyService cacheKeyService,
            PrefixCache prefixCache) {
        this.normalizer = normalizer;
        this.cacheKeyService = cacheKeyService;
        this.prefixCache = prefixCache;
    }

    @GetMapping({"/cache/debug", "/api/v1/cache/debug"})
    public CacheDebugResponse debug(
            @RequestParam(name = "prefix", defaultValue = "") String prefix,
            @RequestParam(name = "rank", defaultValue = "hybrid") String rank) {
        String normalizedPrefix = normalizer.normalize(prefix);
        RankMode rankMode = RankMode.from(rank);
        String key = cacheKeyService.key(rankMode, normalizedPrefix);
        return prefixCache.debug(prefix, normalizedPrefix, key);
    }
}
