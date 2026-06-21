package com.example.typeahead.model;

import java.util.List;

public record SuggestResponse(
        String query,
        String rank,
        List<Suggestion> suggestions,
        CacheInfo cache,
        double latencyMs) {
}
