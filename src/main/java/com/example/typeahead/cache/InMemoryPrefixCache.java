package com.example.typeahead.cache;

import com.example.typeahead.model.CacheDebugResponse;
import com.example.typeahead.model.Suggestion;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "typeahead.cache.provider", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryPrefixCache implements PrefixCache {
    private final ConsistentHashRing ring;
    private final Map<String, Map<String, CacheEntry>> shards = new ConcurrentHashMap<>();

    public InMemoryPrefixCache(CacheProperties properties) {
        List<CacheNode> nodes = properties.nodes().stream().map(CacheNode::new).toList();
        this.ring = new ConsistentHashRing(nodes, properties.virtualNodes());
        nodes.forEach(node -> shards.put(node.id(), new ConcurrentHashMap<>()));
    }

    @Override
    public CacheLookup get(String key) {
        CacheNode node = ring.getNode(key);
        CacheEntry entry = shards.get(node.id()).get(key);
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            if (entry != null) {
                shards.get(node.id()).remove(key);
            }
            return new CacheLookup(false, node.id(), key, 0, List.of());
        }
        return new CacheLookup(true, node.id(), key, entry.ttlSecondsRemaining(), entry.suggestions());
    }

    @Override
    public void put(String key, List<Suggestion> suggestions, Duration ttl) {
        CacheNode node = ring.getNode(key);
        shards.get(node.id()).put(key, new CacheEntry(List.copyOf(suggestions), Instant.now().plus(ttl)));
    }

    @Override
    public void invalidateKeys(Collection<String> keys) {
        for (String key : keys) {
            CacheNode node = ring.getNode(key);
            shards.get(node.id()).remove(key);
        }
    }

    @Override
    public void clear() {
        shards.values().forEach(Map::clear);
    }

    @Override
    public CacheDebugResponse debug(String prefix, String normalizedPrefix, String cacheKey) {
        CacheLookup lookup = get(cacheKey);
        return new CacheDebugResponse(
                prefix,
                normalizedPrefix,
                cacheKey,
                lookup.node(),
                lookup.hit(),
                lookup.ttlSecondsRemaining(),
                Map.of(
                        "physical_nodes", ring.physicalNodeCount(),
                        "virtual_nodes_per_node", ring.virtualNodesPerNode(),
                        "hash_algorithm", "SHA-256"));
    }

    @Override
    public Map<String, Long> nodeDistribution(Collection<String> keys) {
        return ring.getDistribution(keys);
    }

    private record CacheEntry(List<Suggestion> suggestions, Instant expiresAt) {
        long ttlSecondsRemaining() {
            return Math.max(0L, Duration.between(Instant.now(), expiresAt).toSeconds());
        }
    }
}
