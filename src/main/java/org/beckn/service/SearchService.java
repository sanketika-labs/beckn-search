package org.beckn.service;

import org.beckn.model.SearchRequest;
import java.util.List;
import java.util.Map;

public interface SearchService {
    List<Map<String, Object>> search(SearchRequest request);
} 