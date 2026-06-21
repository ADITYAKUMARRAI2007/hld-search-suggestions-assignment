package com.example.typeahead.cache;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "typeahead.cache")
public record CacheProperties(
        long version,
        String provider,
        int virtualNodes,
        int maxPrefixInvalidationLength,
        long countTtlSeconds,
        long hybridTtlSeconds,
        List<String> nodes) {

    public Duration ttlFor(String rank) {
        if ("count".equalsIgnoreCase(rank)) {
            return Duration.ofSeconds(countTtlSeconds);
        }
        return Duration.ofSeconds(hybridTtlSeconds);
    }
}
