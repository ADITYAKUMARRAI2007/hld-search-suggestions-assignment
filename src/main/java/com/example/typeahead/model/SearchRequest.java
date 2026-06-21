package com.example.typeahead.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SearchRequest(
        @NotBlank
        @Size(max = 200)
        String query) {
}
