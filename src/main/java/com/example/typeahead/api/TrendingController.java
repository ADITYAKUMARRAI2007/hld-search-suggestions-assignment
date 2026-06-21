package com.example.typeahead.api;

import com.example.typeahead.model.TrendingResponse;
import com.example.typeahead.trending.TrendingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TrendingController {
    private final TrendingService trendingService;

    public TrendingController(TrendingService trendingService) {
        this.trendingService = trendingService;
    }

    @GetMapping({"/trending", "/api/v1/trending"})
    public TrendingResponse trending(
            @RequestParam(name = "window", defaultValue = "1h") String window,
            @RequestParam(name = "limit", defaultValue = "10") int limit) {
        return trendingService.trending(window, limit);
    }
}
