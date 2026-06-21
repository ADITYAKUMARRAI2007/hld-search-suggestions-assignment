package com.example.typeahead.batch;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "typeahead.batch")
public record BatchProperties(long flushIntervalMs, long maxEvents) {
}
