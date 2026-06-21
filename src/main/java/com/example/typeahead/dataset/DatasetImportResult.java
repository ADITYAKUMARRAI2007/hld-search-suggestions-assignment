package com.example.typeahead.dataset;

import java.time.Instant;

public record DatasetImportResult(
        String datasetName,
        String source,
        long rowsRead,
        long uniqueQueriesLoaded,
        Instant startedAt,
        Instant finishedAt) {
}
