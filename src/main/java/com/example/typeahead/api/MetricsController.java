package com.example.typeahead.api;

import com.example.typeahead.batch.BatchAggregator;
import com.example.typeahead.metrics.TypeaheadMetrics;
import com.example.typeahead.model.MetricsSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MetricsController {
    private final TypeaheadMetrics metrics;
    private final BatchAggregator batchAggregator;

    public MetricsController(TypeaheadMetrics metrics, BatchAggregator batchAggregator) {
        this.metrics = metrics;
        this.batchAggregator = batchAggregator;
    }

    @GetMapping({"/metrics", "/api/v1/metrics/summary"})
    public MetricsSummary summary() {
        return metrics.snapshot(batchAggregator.pendingBufferSize());
    }
}
