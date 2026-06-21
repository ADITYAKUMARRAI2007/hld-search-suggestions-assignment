package com.example.typeahead.api;

import com.example.typeahead.batch.BatchAggregator;
import com.example.typeahead.batch.BatchFlushResult;
import com.example.typeahead.cache.PrefixCache;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminController {
    private final BatchAggregator batchAggregator;
    private final PrefixCache prefixCache;

    public AdminController(BatchAggregator batchAggregator, PrefixCache prefixCache) {
        this.batchAggregator = batchAggregator;
        this.prefixCache = prefixCache;
    }

    @PostMapping("/api/v1/admin/flush")
    public BatchFlushResult flush() {
        return batchAggregator.flush();
    }

    @PostMapping("/api/v1/admin/cache/clear")
    public Map<String, String> clearCache() {
        prefixCache.clear();
        return Map.of("status", "cleared");
    }
}
