package com.example.typeahead.normalize;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QueryNormalizerTest {
    private final QueryNormalizer normalizer = new QueryNormalizer();

    @Test
    void normalizesCaseWhitespaceAndControls() {
        assertThat(normalizer.normalize("  IPHONE\t15\nPRO  ")).isEqualTo("iphone 15 pro");
    }

    @Test
    void rejectsBlankQueries() {
        assertThat(normalizer.isValidSearchQuery("   ")).isFalse();
        assertThat(normalizer.isValidSearchQuery("iphone")).isTrue();
    }
}
