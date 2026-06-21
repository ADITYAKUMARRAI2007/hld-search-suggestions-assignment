package com.example.typeahead.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record Suggestion(
        String query,
        @JsonProperty("historical_count")
        long historicalCount,
        @JsonProperty("recent_count_1h")
        long recentCount1h,
        @JsonProperty("recent_count_24h")
        long recentCount24h,
        long score,
        @JsonProperty("score_breakdown")
        Map<String, Long> scoreBreakdown) {
}
