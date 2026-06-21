package com.example.typeahead.store;

import java.time.Instant;

public record QueryRecord(
        String normalizedQuery,
        String displayQuery,
        long historicalCount,
        Instant lastSearchedAt) {
}
