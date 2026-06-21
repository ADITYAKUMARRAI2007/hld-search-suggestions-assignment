package com.example.typeahead.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ConsistentHashRingTest {
    @Test
    void distributesKeysAcrossNodes() {
        ConsistentHashRing ring = new ConsistentHashRing(List.of(
                new CacheNode("cache-node-1"),
                new CacheNode("cache-node-2"),
                new CacheNode("cache-node-3")), 128);

        assertThat(ring.getDistribution(ConsistentHashRing.sampleKeys("suggest:", 1_000)))
                .hasSize(3);
    }

    @Test
    void addingNodeOnlyRemapsFractionOfKeys() {
        List<String> keys = ConsistentHashRing.sampleKeys("suggest:", 5_000);
        ConsistentHashRing before = new ConsistentHashRing(List.of(
                new CacheNode("cache-node-1"),
                new CacheNode("cache-node-2"),
                new CacheNode("cache-node-3")), 128);
        ConsistentHashRing after = new ConsistentHashRing(List.of(
                new CacheNode("cache-node-1"),
                new CacheNode("cache-node-2"),
                new CacheNode("cache-node-3"),
                new CacheNode("cache-node-4")), 128);

        double remapRatio = ConsistentHashRing.remapRatio(keys, before, after);

        assertThat(remapRatio).isBetween(0.15d, 0.40d);
    }
}
