package com.example.typeahead.batch;

public record BatchFlushResult(
        long rawEvents,
        long uniqueQueries,
        long invalidatedCacheKeys) {
}
