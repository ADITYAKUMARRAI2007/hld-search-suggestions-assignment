package com.example.typeahead.cache;

import com.example.typeahead.ranking.RankMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CacheKeyService {
    private final CacheProperties properties;

    public CacheKeyService(CacheProperties properties) {
        this.properties = properties;
    }

    public String key(RankMode rankMode, String normalizedPrefix) {
        return "suggest:v1:%d:%s:%s".formatted(
                properties.version(),
                rankMode.wireName(),
                normalizedPrefix);
    }

    public List<String> keysForUpdatedQuery(String normalizedQuery) {
        int max = Math.min(properties.maxPrefixInvalidationLength(), normalizedQuery.length());
        List<String> keys = new ArrayList<>(max * RankMode.values().length);
        for (int length = 1; length <= max; length++) {
            String prefix = normalizedQuery.substring(0, length);
            for (RankMode mode : RankMode.values()) {
                keys.add(key(mode, prefix));
            }
        }
        return keys;
    }
}
