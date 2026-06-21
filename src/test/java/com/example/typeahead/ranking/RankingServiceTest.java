package com.example.typeahead.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RankingServiceTest {
    private final RankingService rankingService = new RankingService();

    @Test
    void countModeUsesOnlyHistoricalCount() {
        long score = rankingService.score(RankMode.COUNT, 10_000, 500, 900);
        assertThat(score).isEqualTo(10_000);
    }

    @Test
    void hybridModeRewardsRecentActivity() {
        long baseline = rankingService.score(RankMode.HYBRID, 20_000, 0, 0);
        long boosted = rankingService.score(RankMode.HYBRID, 20_000, 40, 40);
        assertThat(boosted).isGreaterThan(baseline);
    }

    @Test
    void recentSpikeCanBeatHigherHistoricalCount() {
        long highHistorical = rankingService.score(RankMode.HYBRID, 190_000, 0, 0);
        long recentSpike = rankingService.score(RankMode.HYBRID, 20_000, 40, 40);
        assertThat(recentSpike).isGreaterThan(highHistorical);
    }
}
