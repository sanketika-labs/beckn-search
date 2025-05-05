package org.beckn.search.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.beckn.search.model.SearchRequestDto;
import org.beckn.search.model.SearchResponseDto;
import org.beckn.search.transformer.SearchResponseTransformer;
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
    private final SearchResponseTransformer responseTransformer;

    @Value("${elasticsearch.max.results:100}")
    private int maxResults;

    @Value("${elasticsearch.default.page.size:10}")
    private int defaultPageSize;

    public SearchQueryBuilder.LogicalOperator parseOperator(String operator) {
        try {
            return SearchQueryBuilder.LogicalOperator.valueOf(operator.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid operator. Must be either 'AND' or 'OR'");
        }
    }

    @Cacheable(value = "searchResults", key = "#request.toString() + #pageNum + #pageSize + #operator")
    public SearchResponse<Map> search(SearchRequestDto request, int pageNum, int pageSize, SearchQueryBuilder.LogicalOperator operator) throws IOException {
        if (request.getContext() == null || request.getContext().getDomain() == null) {
            throw new IllegalArgumentException("Domain must be specified in the request context");
        }
        
        String indexName = request.getContext().getDomain().toLowerCase();
        
        // Check if index exists
        boolean indexExists = elasticsearchClient.indices().exists(e -> e.index(indexName)).value();
        if (!indexExists) {
            throw new IllegalArgumentException("Index '" + indexName + "' does not exist");
        }
        
        var query = queryBuilder.buildSearchQuery(request, operator);
        
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
        return search(request, 0, defaultPageSize, SearchQueryBuilder.LogicalOperator.AND);
    }

    public SearchResponse<Map> search(SearchRequestDto request, String operator) throws IOException {
        return search(request, 0, defaultPageSize, parseOperator(operator));
    }

    @Cacheable(value = "rawCatalog", key = "#request.toString() + #pageNum + #pageSize + #operator")
    public String searchAndGetRawCatalog(SearchRequestDto request, int pageNum, int pageSize, SearchQueryBuilder.LogicalOperator operator) throws IOException {
        SearchResponse<Map> response = search(request, pageNum, pageSize, operator);
        
        // Extract raw_catalog from all hits
        List<String> rawCatalogs = response.hits().hits().stream()
            .map(hit -> (Map<String, Object>) hit.source())
            .filter(source -> source != null && source.containsKey("raw_catalog"))
            .map(source -> source.get("raw_catalog").toString())
            .collect(Collectors.toList());
        
        // If no hits found, return empty array
        if (rawCatalogs.isEmpty()) {
            return "[]";
        }
        
        // Return the raw catalogs as a JSON array
        return objectMapper.writeValueAsString(rawCatalogs);
    }

    public String searchAndGetRawCatalog(SearchRequestDto request) throws IOException {
        return searchAndGetRawCatalog(request, 0, defaultPageSize, SearchQueryBuilder.LogicalOperator.AND);
    }

    public String searchAndGetRawCatalog(SearchRequestDto request, String operator) throws IOException {
        return searchAndGetRawCatalog(request, 0, defaultPageSize, parseOperator(operator));
    }

    @Cacheable(value = "searchResponse", key = "#request.toString() + #pageNum + #pageSize + #operator")
    public SearchResponseDto searchAndGetResponse(SearchRequestDto request, int pageNum, int pageSize, SearchQueryBuilder.LogicalOperator operator) throws IOException {
        String rawCatalog = searchAndGetRawCatalog(request, pageNum, pageSize, operator);
        return responseTransformer.transformToResponse(rawCatalog);
    }

    public SearchResponseDto searchAndGetResponse(SearchRequestDto request) throws IOException {
        return searchAndGetResponse(request, 0, defaultPageSize, SearchQueryBuilder.LogicalOperator.AND);
    }

    public SearchResponseDto searchAndGetResponse(SearchRequestDto request, String operator) throws IOException {
        return searchAndGetResponse(request, 0, defaultPageSize, parseOperator(operator));
    }
} 