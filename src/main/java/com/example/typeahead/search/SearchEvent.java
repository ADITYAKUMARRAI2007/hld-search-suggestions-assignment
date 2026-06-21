package com.example.typeahead.search;

import java.time.Instant;

public record SearchEvent(
        String eventId,
        String normalizedQuery,
        String displayQuery,
        Instant occurredAt,
        String source) {
}
