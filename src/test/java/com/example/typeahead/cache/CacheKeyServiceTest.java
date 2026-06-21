package com.example.typeahead.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.typeahead.ranking.RankMode;
import java.util.List;
import org.junit.jupiter.api.Test;

class CacheKeyServiceTest {
    @Test
    void buildsVersionedRankAwareKeys() {
        CacheKeyService service = new CacheKeyService(new CacheProperties(
                7, "in-memory", 128, 20, 180, 15, List.of("cache-node-1")));

        assertThat(service.key(RankMode.HYBRID, "iph"))
                .isEqualTo("suggest:v1:7:hybrid:iph");
    }

    @Test
    void invalidationKeysCoverPrefixesAndRankingModes() {
        CacheKeyService service = new CacheKeyService(new CacheProperties(
                1, "in-memory", 128, 3, 180, 15, List.of("cache-node-1")));

        assertThat(service.keysForUpdatedQuery("iphone"))
                .containsExactly(
                        "suggest:v1:1:count:i",
                        "suggest:v1:1:hybrid:i",
                        "suggest:v1:1:count:ip",
                        "suggest:v1:1:hybrid:ip",
                        "suggest:v1:1:count:iph",
                        "suggest:v1:1:hybrid:iph");
    }
}
