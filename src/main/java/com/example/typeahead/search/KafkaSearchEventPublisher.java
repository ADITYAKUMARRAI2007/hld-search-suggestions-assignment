package com.example.typeahead.search;

import com.example.typeahead.metrics.TypeaheadMetrics;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "typeahead.events", havingValue = "kafka")
public class KafkaSearchEventPublisher implements SearchEventPublisher {
    private static final String TOPIC = "search-events";

    private final KafkaTemplate<String, SearchEvent> kafkaTemplate;
    private final TypeaheadMetrics metrics;

    public KafkaSearchEventPublisher(KafkaTemplate<String, SearchEvent> kafkaTemplate, TypeaheadMetrics metrics) {
        this.kafkaTemplate = kafkaTemplate;
        this.metrics = metrics;
    }

    @Override
    public void publish(SearchEvent event) {
        try {
            kafkaTemplate.send(TOPIC, event.normalizedQuery(), event).get(2, TimeUnit.SECONDS);
            metrics.recordKafkaEventPublished();
        } catch (Exception ex) {
            throw new IllegalStateException("failed to publish search event to Kafka", ex);
        }
    }
}
