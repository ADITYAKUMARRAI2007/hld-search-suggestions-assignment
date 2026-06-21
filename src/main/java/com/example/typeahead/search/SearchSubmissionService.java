package com.example.typeahead.search;

import com.example.typeahead.metrics.TypeaheadMetrics;
import com.example.typeahead.model.SearchRequest;
import com.example.typeahead.model.SearchResponse;
import com.example.typeahead.normalize.QueryNormalizer;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SearchSubmissionService {
    private final QueryNormalizer normalizer;
    private final SearchEventPublisher publisher;
    private final TypeaheadMetrics metrics;

    public SearchSubmissionService(
            QueryNormalizer normalizer,
            SearchEventPublisher publisher,
            TypeaheadMetrics metrics) {
        this.normalizer = normalizer;
        this.publisher = publisher;
        this.metrics = metrics;
    }

    public SearchResponse submit(SearchRequest request) {
        String normalizedQuery = normalizer.normalize(request.query());
        if (!normalizer.isValidSearchQuery(normalizedQuery)) {
            throw new IllegalArgumentException("query must be non-empty and at most 200 characters");
        }
        SearchEvent event = new SearchEvent(
                UUID.randomUUID().toString(),
                normalizedQuery,
                normalizedQuery,
                Instant.now(),
                "web");
        metrics.recordSearchEventReceived();
        publisher.publish(event);
        return new SearchResponse("Searched", "accepted", normalizedQuery, event.eventId());
    }
}
