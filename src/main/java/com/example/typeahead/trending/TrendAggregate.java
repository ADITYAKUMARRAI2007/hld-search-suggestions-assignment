package com.example.typeahead.trending;

public record TrendAggregate(
        String normalizedQuery,
        String displayQuery,
        long count) {
}
