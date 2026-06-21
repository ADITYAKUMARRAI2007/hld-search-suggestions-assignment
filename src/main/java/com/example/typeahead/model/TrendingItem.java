package com.example.typeahead.model;

public record TrendingItem(
        String query,
        long recentCount,
        long historicalCount,
        long score) {
}
