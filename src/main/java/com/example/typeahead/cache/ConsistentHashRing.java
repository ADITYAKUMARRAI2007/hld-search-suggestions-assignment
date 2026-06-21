package com.example.typeahead.cache;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class ConsistentHashRing {
    private final int virtualNodesPerNode;
    private final NavigableMap<Long, CacheNode> ring = new TreeMap<>();

    public ConsistentHashRing(Collection<CacheNode> nodes, int virtualNodesPerNode) {
        if (virtualNodesPerNode <= 0) {
            throw new IllegalArgumentException("virtualNodesPerNode must be positive");
        }
        this.virtualNodesPerNode = virtualNodesPerNode;
        nodes.forEach(this::addNode);
    }

    public synchronized void addNode(CacheNode node) {
        for (int i = 0; i < virtualNodesPerNode; i++) {
            ring.put(hash(node.id() + "#" + i), node);
        }
    }

    public synchronized void removeNode(String nodeId) {
        List<Long> keysToRemove = ring.entrySet()
                .stream()
                .filter(entry -> entry.getValue().id().equals(nodeId))
                .map(Map.Entry::getKey)
                .toList();
        keysToRemove.forEach(ring::remove);
    }

    public synchronized CacheNode getNode(String key) {
        if (ring.isEmpty()) {
            throw new IllegalStateException("consistent hash ring has no cache nodes");
        }
        long keyHash = hash(key);
        Map.Entry<Long, CacheNode> entry = ring.ceilingEntry(keyHash);
        if (entry == null) {
            entry = ring.firstEntry();
        }
        return entry.getValue();
    }

    public synchronized Map<String, Long> getDistribution(Collection<String> keys) {
        return keys.stream()
                .map(key -> getNode(key).id())
                .collect(Collectors.groupingBy(node -> node, Collectors.counting()));
    }

    public synchronized int physicalNodeCount() {
        return (int) ring.values().stream().map(CacheNode::id).distinct().count();
    }

    public int virtualNodesPerNode() {
        return virtualNodesPerNode;
    }

    public static double remapRatio(Collection<String> keys, ConsistentHashRing before, ConsistentHashRing after) {
        if (keys.isEmpty()) {
            return 0d;
        }
        long changed = keys.stream()
                .filter(key -> !before.getNode(key).id().equals(after.getNode(key).id()))
                .count();
        return changed / (double) keys.size();
    }

    public static List<String> sampleKeys(String prefix, int count) {
        List<String> keys = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            keys.add(prefix + i);
        }
        return keys;
    }

    private static long hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(bytes).getLong() & Long.MAX_VALUE;
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
