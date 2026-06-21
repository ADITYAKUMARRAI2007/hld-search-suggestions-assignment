package com.example.typeahead.cache;

import com.example.typeahead.model.CacheDebugResponse;
import com.example.typeahead.model.Suggestion;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "typeahead.cache.provider", havingValue = "redis")
public class RedisPrefixCache implements PrefixCache {
    private final ConsistentHashRing ring;
    private final Map<String, StringRedisTemplate> shards = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public RedisPrefixCache(CacheProperties properties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        List<CacheNode> nodes = properties.nodes().stream().map(CacheNode::new).toList();
        this.ring = new ConsistentHashRing(nodes, properties.virtualNodes());
        nodes.forEach(node -> shards.put(node.id(), templateFor(node.id())));
    }

    @Override
    public CacheLookup get(String key) {
        CacheNode node = ring.getNode(key);
        StringRedisTemplate template = shards.get(node.id());
        String json = template.opsForValue().get(key);
        if (json == null) {
            return new CacheLookup(false, node.id(), key, 0, List.of());
        }
        try {
            List<Suggestion> suggestions = objectMapper.readValue(json, new TypeReference<>() {});
            Long ttl = template.getExpire(key);
            return new CacheLookup(true, node.id(), key, ttl == null ? 0 : ttl, suggestions);
        } catch (Exception ex) {
            template.delete(key);
            return new CacheLookup(false, node.id(), key, 0, List.of());
        }
    }

    @Override
    public void put(String key, List<Suggestion> suggestions, Duration ttl) {
        CacheNode node = ring.getNode(key);
        try {
            shards.get(node.id()).opsForValue().set(key, objectMapper.writeValueAsString(suggestions), ttl);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to write prefix cache", ex);
        }
    }

    @Override
    public void invalidateKeys(Collection<String> keys) {
        for (String key : keys) {
            CacheNode node = ring.getNode(key);
            shards.get(node.id()).delete(key);
        }
    }

    @Override
    public void clear() {
        shards.values().forEach(template -> {
            var connection = template.getConnectionFactory();
            if (connection != null) {
                connection.getConnection().serverCommands().flushDb();
            }
        });
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

    private StringRedisTemplate templateFor(String nodeId) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(hostFor(nodeId), 6379);
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();
        return new StringRedisTemplate(connectionFactory);
    }

    private String hostFor(String nodeId) {
        return nodeId.replace("cache-node", "redis");
    }
}
