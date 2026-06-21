package com.example.typeahead.store;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface QueryStore {
    QueryRecord upsertImported(String normalizedQuery, String displayQuery, long historicalCount, Instant searchedAt);

    List<QueryRecord> incrementCounts(Collection<QueryAggregate> updates);

    List<QueryRecord> findAll();

    Optional<QueryRecord> find(String normalizedQuery);

    long count();
}
