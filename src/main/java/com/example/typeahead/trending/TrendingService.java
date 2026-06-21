package com.example.typeahead.trending;

import com.example.typeahead.model.TrendingResponse;
import org.springframework.stereotype.Service;

@Service
public class TrendingService {
    private final TrendBucketService trendBucketService;

    public TrendingService(TrendBucketService trendBucketService) {
        this.trendBucketService = trendBucketService;
    }

    public TrendingResponse trending(String windowValue, int limit) {
        TrendWindow window = TrendWindow.from(windowValue);
        return new TrendingResponse(window.wireName(), trendBucketService.top(window, limit));
    }
}
