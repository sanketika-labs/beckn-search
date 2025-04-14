package org.beckn.controller;

import lombok.RequiredArgsConstructor;
import org.beckn.model.SearchRequest;
import org.beckn.service.SearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @PostMapping("/search")
    public ResponseEntity<List<Map<String, Object>>> search(@RequestBody SearchRequest request) {
        return ResponseEntity.ok(searchService.search(request));
    }
} 