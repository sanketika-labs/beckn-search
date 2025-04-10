package org.beckn.service;

import org.beckn.model.SearchRequest;
import org.beckn.model.SearchDocument;
import org.springframework.data.elasticsearch.core.SearchHits;

public interface SearchService {
    SearchHits<SearchDocument> search(SearchRequest request);
} 