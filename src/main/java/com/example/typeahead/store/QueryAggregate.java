package com.example.typeahead.store;

import java.time.Instant;

public record QueryAggregate(
        String normalizedQuery,
        String displayQuery,
        long increment,
        Instant occurredAt) {
}
