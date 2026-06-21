package com.example.typeahead.model;

public record SearchResponse(
        String message,
        String status,
        String query,
        String eventId) {
}
