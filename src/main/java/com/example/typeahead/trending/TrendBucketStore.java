package com.example.typeahead.trending;

import com.example.typeahead.store.QueryAggregate;
import java.time.Duration;
import java.util.Collection;
import java.util.List;

public interface TrendBucketStore {
    void record(Collection<QueryAggregate> aggregates, int bucketMinutes);

    long recentCount(String normalizedQuery, TrendWindow window, int bucketMinutes);

    List<TrendAggregate> totals(Duration window, int bucketMinutes);
}
