package com.example.typeahead.dataset;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "typeahead.dataset")
public record DatasetImportProperties(String importPath, int importLimit) {
}
