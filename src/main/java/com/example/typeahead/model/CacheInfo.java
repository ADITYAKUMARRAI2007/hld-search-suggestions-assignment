package com.example.typeahead.model;

public record CacheInfo(
        boolean hit,
        String node,
        String key,
        long ttlSecondsRemaining) {
}
