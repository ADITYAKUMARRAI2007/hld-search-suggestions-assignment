package com.example.typeahead.model;

import java.util.Map;

public record CacheDebugResponse(
        String prefix,
        String normalizedPrefix,
        String cacheKey,
        String node,
        boolean hit,
        long ttlSecondsRemaining,
        Map<String, Object> ring) {
}
