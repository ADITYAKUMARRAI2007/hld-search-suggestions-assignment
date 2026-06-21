package com.example.typeahead.model;

import java.util.List;

public record TrendingResponse(
        String window,
        List<TrendingItem> trending) {
}
