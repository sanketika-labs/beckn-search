package org.beckn.service;

import java.util.List;
import org.beckn.model.SearchRequest;
import org.beckn.model.SearchDocument;
import org.springframework.data.elasticsearch.core.SearchHits;

public interface SearchService {
    List<SearchDocument> search(SearchRequest request);
} 