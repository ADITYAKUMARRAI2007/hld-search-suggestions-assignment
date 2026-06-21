package com.example.typeahead.suggestion;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "typeahead.opensearch")
public record OpenSearchProperties(String baseUrl, String index) {
}
