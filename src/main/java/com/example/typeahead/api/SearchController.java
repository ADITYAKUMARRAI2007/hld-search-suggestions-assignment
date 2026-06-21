package com.example.typeahead.api;

import com.example.typeahead.model.SearchRequest;
import com.example.typeahead.model.SearchResponse;
import com.example.typeahead.search.SearchSubmissionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SearchController {
    private final SearchSubmissionService searchSubmissionService;

    public SearchController(SearchSubmissionService searchSubmissionService) {
        this.searchSubmissionService = searchSubmissionService;
    }

    @PostMapping({"/search", "/api/v1/search"})
    public SearchResponse search(@Valid @RequestBody SearchRequest request) {
        return searchSubmissionService.submit(request);
    }
}
