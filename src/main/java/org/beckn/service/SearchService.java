package org.beckn.service;

import org.beckn.model.SearchRequest;
import org.beckn.model.SearchResponse;

public interface SearchService {
    SearchResponse search(SearchRequest request);
} 