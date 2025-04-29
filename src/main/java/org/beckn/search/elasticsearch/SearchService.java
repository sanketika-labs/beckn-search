package org.beckn.search.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.beckn.search.model.SearchRequestDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchService {
    private final ElasticsearchClient elasticsearchClient;
    private final SearchQueryBuilder queryBuilder;
    private final ObjectMapper objectMapper;

    @Value("${elasticsearch.max.results:100}")
    private int maxResults;

    @Value("${elasticsearch.default.page.size:10}")
    private int defaultPageSize;

    @Cacheable(value = "searchResults", key = "#request.toString() + #pageNum + #pageSize")
    public SearchResponse<Map> search(SearchRequestDto request, int pageNum, int pageSize) throws IOException {
        if (request.getContext() == null || request.getContext().getDomain() == null) {
            throw new IllegalArgumentException("Domain must be specified in the request context");
        }
        
        String indexName = request.getContext().getDomain().toLowerCase();
        
        // Check if index exists
        boolean indexExists = elasticsearchClient.indices().exists(e -> e.index(indexName)).value();
        if (!indexExists) {
            throw new IllegalArgumentException("Index '" + indexName + "' does not exist");
        }
        
        var query = queryBuilder.buildSearchQuery(request);
        
        // Validate and adjust pagination parameters
        final int validatedSize = Math.min(pageSize > 0 ? pageSize : defaultPageSize, maxResults);
        final int validatedPage = Math.max(pageNum, 0);
        
        try {
            return elasticsearchClient.search(s -> s
                    .index(indexName)
                    .query(query)
                    .from(validatedPage * validatedSize)
                    .size(validatedSize),
                Map.class
            );
        } catch (Exception e) {
            throw new RuntimeException("Error executing search: " + e.getMessage(), e);
        }
    }

    public SearchResponse<Map> search(SearchRequestDto request) throws IOException {
        return search(request, 0, defaultPageSize);
    }

    @Cacheable(value = "rawCatalog", key = "#request.toString() + #pageNum + #pageSize")
    public String searchAndGetRawCatalog(SearchRequestDto request, int pageNum, int pageSize) throws IOException {
        SearchResponse<Map> response = search(request, pageNum, pageSize);
        Map<String, Object> catalogWrapper = new HashMap<>();
        Map<String, Object> message = new HashMap<>();
        Map<String, Object> catalog = new HashMap<>();
        
        // Add descriptor
        Map<String, Object> descriptor = new HashMap<>();
        descriptor.put("name", "EcoCharge-Retail-Catalog");
        catalog.put("descriptor", descriptor);
        
        // Add search results as providers
        if (!response.hits().hits().isEmpty()) {
            List<Map<String, Object>> providers = response.hits().hits().stream()
                .map(hit -> (Map<String, Object>) hit.source())
                .collect(Collectors.toList());
            catalog.put("providers", providers);
        } else {
            catalog.put("providers", Collections.emptyList());
        }
        
        message.put("catalog", catalog);
        catalogWrapper.put("message", message);
        
        return objectMapper.writeValueAsString(catalogWrapper);
    }

    public String searchAndGetRawCatalog(SearchRequestDto request) throws IOException {
        return searchAndGetRawCatalog(request, 0, defaultPageSize);
    }
} 