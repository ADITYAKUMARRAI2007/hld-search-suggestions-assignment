package com.example.typeahead.ranking;

import java.util.Locale;

public enum RankMode {
    COUNT,
    HYBRID;

    public static RankMode from(String value) {
        if (value == null || value.isBlank()) {
            return HYBRID;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "count", "overall", "basic" -> COUNT;
            case "hybrid", "trending", "trend" -> HYBRID;
            default -> HYBRID;
        };
    }

    public String wireName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
