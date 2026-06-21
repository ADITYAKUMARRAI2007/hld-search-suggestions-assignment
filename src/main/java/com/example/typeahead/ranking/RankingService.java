package com.example.typeahead.ranking;

import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RankingService {
    public long score(RankMode mode, long historicalCount, long recentCount1h, long recentCount24h) {
        if (mode == RankMode.COUNT) {
            return historicalCount;
        }
        return scoreBreakdown(historicalCount, recentCount1h, recentCount24h)
                .values()
                .stream()
                .mapToLong(Long::longValue)
                .sum();
    }

    public Map<String, Long> scoreBreakdown(long historicalCount, long recentCount1h, long recentCount24h) {
        long historical = Math.round(Math.log10(historicalCount + 1.0d) * 100_000d);
        long recent1h = Math.round(Math.log10(recentCount1h + 1.0d) * 60_000d);
        long recent24h = Math.round(Math.log10(recentCount24h + 1.0d) * 25_000d);
        return Map.of(
                "historical_component", historical,
                "recent_1h_component", recent1h,
                "recent_24h_component", recent24h);
    }
}
