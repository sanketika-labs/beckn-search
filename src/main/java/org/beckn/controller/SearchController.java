package org.beckn.controller;

import lombok.RequiredArgsConstructor;
import org.beckn.model.SearchRequest;
import org.beckn.model.SearchDocument;
import org.beckn.service.SearchService;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @PostMapping("/search")
    public ResponseEntity<SearchHits<SearchDocument>> search(@RequestBody SearchRequest request) {
        return ResponseEntity.ok(searchService.search(request));
    }
} 