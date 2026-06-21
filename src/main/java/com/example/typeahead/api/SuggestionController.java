package com.example.typeahead.api;

import com.example.typeahead.model.SuggestResponse;
import com.example.typeahead.suggestion.SuggestionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SuggestionController {
    private final SuggestionService suggestionService;

    public SuggestionController(SuggestionService suggestionService) {
        this.suggestionService = suggestionService;
    }

    @GetMapping({"/suggest", "/api/v1/suggest"})
    public SuggestResponse suggest(
            @RequestParam(name = "q", defaultValue = "") String query,
            @RequestParam(name = "rank", defaultValue = "hybrid") String rank,
            @RequestParam(name = "debug", defaultValue = "false") boolean debug) {
        return suggestionService.suggest(query, rank, debug);
    }
}
