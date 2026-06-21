package com.example.typeahead.search;

import com.example.typeahead.batch.BatchAggregator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "typeahead.events", havingValue = "kafka")
public class KafkaSearchEventConsumer {
    private final BatchAggregator batchAggregator;

    public KafkaSearchEventConsumer(BatchAggregator batchAggregator) {
        this.batchAggregator = batchAggregator;
    }

    @KafkaListener(topics = "search-events", groupId = "typeahead-batch-writer")
    public void consume(SearchEvent event) {
        batchAggregator.accept(event);
    }
}
