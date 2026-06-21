package com.example.typeahead.cache;

import com.example.typeahead.model.CacheDebugResponse;
import com.example.typeahead.model.Suggestion;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface PrefixCache {
    CacheLookup get(String key);

    void put(String key, List<Suggestion> suggestions, Duration ttl);

    void invalidateKeys(Collection<String> keys);

    void clear();

    CacheDebugResponse debug(String prefix, String normalizedPrefix, String cacheKey);

    Map<String, Long> nodeDistribution(Collection<String> keys);
}
