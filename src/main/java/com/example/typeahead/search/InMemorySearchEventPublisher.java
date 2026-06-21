package com.example.typeahead.search;

import com.example.typeahead.batch.BatchAggregator;
import com.example.typeahead.metrics.TypeaheadMetrics;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "typeahead.events", havingValue = "in-memory", matchIfMissing = true)
public class InMemorySearchEventPublisher implements SearchEventPublisher {
    private final BatchAggregator batchAggregator;
    private final TypeaheadMetrics metrics;

    public InMemorySearchEventPublisher(BatchAggregator batchAggregator, TypeaheadMetrics metrics) {
        this.batchAggregator = batchAggregator;
        this.metrics = metrics;
    }

    @Override
    public void publish(SearchEvent event) {
        metrics.recordKafkaEventPublished();
        batchAggregator.accept(event);
    }
}
