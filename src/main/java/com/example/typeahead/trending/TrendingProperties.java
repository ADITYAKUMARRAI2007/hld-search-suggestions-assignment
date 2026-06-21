package com.example.typeahead.trending;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "typeahead.trending")
public record TrendingProperties(int bucketMinutes) {
}
