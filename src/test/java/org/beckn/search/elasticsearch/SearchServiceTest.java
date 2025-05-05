package org.beckn.search.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.beckn.search.model.SearchRequestDto;
import org.beckn.search.model.SearchResponseDto;
import org.beckn.search.model.Context;
import org.beckn.search.transformer.SearchResponseTransformer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SearchServiceTest {

    @Mock
    private ElasticsearchClient elasticsearchClient;

    @Mock
    private SearchQueryBuilder queryBuilder;

    @Mock
    private SearchResponseTransformer responseTransformer;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private SearchService searchService;

    private SearchRequestDto mockRequest;
    private SearchResponseDto mockResponse;
    private String mockRawCatalog;
    private SearchResponse<Map<String, Object>> mockSearchResponse;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() throws IOException {
        // Setup mock request
        mockRequest = new SearchRequestDto();
        Context context = new Context();
        context.setDomain("retail");
        mockRequest.setContext(context);

        // Setup mock response
        mockResponse = new SearchResponseDto();
        SearchResponseDto.Message message = new SearchResponseDto.Message();
        SearchResponseDto.Catalog catalog = new SearchResponseDto.Catalog();
        message.setCatalog(catalog);
        mockResponse.setMessage(message);

        // Setup mock raw catalog
        mockRawCatalog = """
            {
                "message": {
                    "catalog": {
                        "descriptor": {
                            "name": "Test Catalog"
                        }
                    }
                }
            }
            """;

        // Setup mock Elasticsearch response
        Map<String, Object> source = new HashMap<>();
        source.put("raw_catalog", mockRawCatalog);
        
        Hit<Map<String, Object>> hit = mock(Hit.class);
        when(hit.source()).thenReturn(source);
        
        HitsMetadata<Map<String, Object>> hitsMetadata = mock(HitsMetadata.class);
        when(hitsMetadata.hits()).thenReturn(List.of(hit));
        
        mockSearchResponse = mock(SearchResponse.class);
        when(mockSearchResponse.hits()).thenReturn(hitsMetadata);

        // Setup mock indices client
        var indicesClient = mock(ElasticsearchIndicesClient.class);
        doReturn(new co.elastic.clients.transport.endpoints.BooleanResponse(true))
            .when(indicesClient)
            .exists(any(Function.class));
        when(elasticsearchClient.indices()).thenReturn(indicesClient);

        // Setup mock query builder
        when(queryBuilder.buildSearchQuery(any(), any(SearchQueryBuilder.LogicalOperator.class)))
            .thenReturn(mock(Query.class));

        // Setup mock elasticsearch client search
        when(elasticsearchClient.search(any(Function.class), eq(Map.class)))
            .thenReturn(mockSearchResponse);

        // Setup mock transformer
        when(responseTransformer.transformToResponse(any()))
            .thenReturn(mockResponse);
    }

    @Test
    void testSearch() throws IOException {
        SearchResponse<Map> response = searchService.search(mockRequest);
        assertNotNull(response);
        assertEquals(1, response.hits().hits().size());
        verify(elasticsearchClient.indices()).exists(any(Function.class));
    }

    @Test
    void testSearchAndGetRawCatalog() throws IOException {
        String rawCatalog = searchService.searchAndGetRawCatalog(mockRequest);
        assertNotNull(rawCatalog);
        assertTrue(rawCatalog.contains("Test Catalog"));
        verify(elasticsearchClient.indices()).exists(any(Function.class));
    }

    @Test
    void testSearchAndGetResponse() throws IOException {
        SearchResponseDto response = searchService.searchAndGetResponse(mockRequest);
        assertNotNull(response);
        assertNotNull(response.getMessage());
        assertNotNull(response.getMessage().getCatalog());
        verify(elasticsearchClient.indices()).exists(any(Function.class));
    }

    @Test
    void testSearchWithInvalidDomain() {
        mockRequest.getContext().setDomain(null);
        assertThrows(IllegalArgumentException.class, () -> searchService.search(mockRequest));
    }

    @Test
    void testSearchWithNonExistentIndex() throws IOException {
        // Setup mock indices client to return false for exists check
        var indicesClient = mock(ElasticsearchIndicesClient.class);
        doReturn(new co.elastic.clients.transport.endpoints.BooleanResponse(false))
            .when(indicesClient)
            .exists(any(Function.class));
        when(elasticsearchClient.indices()).thenReturn(indicesClient);

        assertThrows(IllegalArgumentException.class, () -> searchService.search(mockRequest));
    }
} 