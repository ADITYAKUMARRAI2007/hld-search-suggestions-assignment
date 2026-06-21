package com.example.typeahead.cache;

import com.example.typeahead.model.Suggestion;
import java.util.List;

public record CacheLookup(
        boolean hit,
        String node,
        String key,
        long ttlSecondsRemaining,
        List<Suggestion> suggestions) {
}
