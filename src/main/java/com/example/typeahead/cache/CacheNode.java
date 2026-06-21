package com.example.typeahead.cache;

public record CacheNode(String id) {
    public CacheNode {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("cache node id must not be blank");
        }
    }
}
