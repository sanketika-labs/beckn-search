package org.beckn.search.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.beckn.search.elasticsearch.SearchService;
import org.beckn.search.elasticsearch.SearchQueryBuilder;
import org.beckn.search.model.*;
import org.beckn.search.validation.SearchRequestValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SearchController.class)
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SearchService searchService;

    @MockBean
    private SearchRequestValidator requestValidator;

    private String sampleJson;
    private SearchResponseDto mockResponse;

    @BeforeEach
    void setUp() throws Exception {
        ClassPathResource resource = new ClassPathResource("search_intent_body_sample.json");
        sampleJson = new String(Files.readAllBytes(resource.getFile().toPath()));

        mockResponse = createMockResponse("EcoCharge-Retail-Catalog", "Local Store");
    }

    private String loadTestFile(String filename) throws Exception {
        ClassPathResource resource = new ClassPathResource(filename);
        return new String(Files.readAllBytes(resource.getFile().toPath()));
    }

    private SearchRequestDto createRequestWithPagination(int page, int limit) throws Exception {
        String json = loadTestFile("search_intent_body_sample.json");
        SearchRequestDto request = objectMapper.readValue(json, SearchRequestDto.class);
        request.getMessage().getIntent().setPage(page);
        request.getMessage().getIntent().setLimit(limit);
        return request;
    }

    private SearchResponseDto createMockResponse(String catalogName, String providerName) {
        SearchResponseDto response = new SearchResponseDto();
        
        // Set context
        Context context = new Context();
        context.setDomain("retail");
        
        Location location = new Location();
        Location.Country country = new Location.Country();
        country.setName("India");
        country.setCode("IND");
        location.setCountry(country);
        
        Location.City city = new Location.City();
        city.setName("Bangalore");
        city.setCode("std:080");
        location.setCity(city);
        
        context.setLocation(location);
        context.setBapId("beckn-search");
        context.setBapUri("https://search.becknprotocol.io");
        context.setTransactionId("txn-123");
        context.setMessageId("msg-123");
        context.setTimestamp("2024-03-15T10:00:00Z");
        response.setContext(context);
        
        // Set message with catalog
        SearchResponseDto.Message message = new SearchResponseDto.Message();
        SearchResponseDto.Catalog catalog = new SearchResponseDto.Catalog();
        
        // Set catalog descriptor
        SearchResponseDto.Descriptor descriptor = new SearchResponseDto.Descriptor();
        descriptor.setName(catalogName);
        descriptor.setCode("CATALOG-001");
        descriptor.setShortDesc("Retail catalog");
        descriptor.setLongDesc("Comprehensive retail catalog with multiple providers");
        catalog.setDescriptor(descriptor);
        
        // Add a mock provider
        try {
            String providersJson = String.format("""
                [
                    {
                        "id": "provider-1",
                        "descriptor": {
                            "name": "%s",
                            "code": "PRV-001"
                        },
                            "categories": [
                            {
                                "id": "grocery",
                                "descriptor": {
                                    "name": "Grocery",
                                    "code": "CAT-001"
                                }
                            }
                        ]
                    }
                ]
                """, providerName);
            catalog.setProviders(objectMapper.readTree(providersJson));
        } catch (Exception e) {
            // In case of JSON parsing error, set empty providers
            catalog.setProviders(objectMapper.createArrayNode());
        }
        
        message.setCatalog(catalog);
        response.setMessage(message);
        
        return response;
    }

    @Test
    void testSuccessfulSearch() throws Exception {
        String requestJson = loadTestFile("search_intent_body_sample.json");
        SearchResponseDto mockResponse = createMockResponse("EcoCharge-Retail-Catalog", "Local Store");

        // Mock validator to do nothing (validation passes)
        doNothing().when(requestValidator).validate(any(SearchRequestDto.class));

        when(searchService.searchAndGetResponse(any(SearchRequestDto.class), eq("OR")))
            .thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.context.domain").value("retail"))
            .andExpect(jsonPath("$.context.location.country.code").value("IND"))
            .andExpect(jsonPath("$.context.location.city.code").value("std:080"))
            .andExpect(jsonPath("$.message.catalog.descriptor.name").value("EcoCharge-Retail-Catalog"))
            .andExpect(jsonPath("$.message.catalog.descriptor.code").value("CATALOG-001"))
            .andExpect(jsonPath("$.message.catalog.providers[0].id").value("provider-1"))
            .andExpect(jsonPath("$.message.catalog.providers[0].descriptor.name").value("Local Store"))
            .andExpect(jsonPath("$.message.catalog.providers[0].categories[0].id").value("grocery"));
    }

    @Test
    void testSearchWithPagination() throws Exception {
        SearchRequestDto request = createRequestWithPagination(1, 20);
        String requestJson = objectMapper.writeValueAsString(request);
        String catalogJson = "{\"message\":{\"catalog\":{}}}";

        when(searchService.searchAndGetRawCatalog(any(SearchRequestDto.class), eq(1), eq(20), eq(SearchQueryBuilder.LogicalOperator.OR)))
            .thenReturn(catalogJson);
        doNothing().when(requestValidator).validate(any());

        mockMvc.perform(post("/api/v1/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message.catalog").exists());
    }

    @Test
    void testSearchWithAndOperator() throws Exception {
        // Setup mock responses
        when(searchService.searchAndGetResponse(any(SearchRequestDto.class), eq("AND")))
            .thenReturn(mockResponse);
        doNothing().when(requestValidator).validate(any());

        // Perform request with AND operator
        mockMvc.perform(post("/api/v1/search")
                .param("operator", "AND")
                .contentType(MediaType.APPLICATION_JSON)
                .content(sampleJson))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message.catalog").exists());

        // Verify the service was called with AND operator
        verify(searchService).searchAndGetResponse(any(SearchRequestDto.class), eq("AND"));
    }

    @Test
    void testSearchWithOrOperator() throws Exception {
        // Setup mock responses
        when(searchService.searchAndGetResponse(any(SearchRequestDto.class), eq("OR")))
            .thenReturn(mockResponse);
        doNothing().when(requestValidator).validate(any());

        // Perform request with OR operator (default)
        mockMvc.perform(post("/api/v1/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(sampleJson))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message.catalog").exists());

        // Verify the service was called with OR operator
        verify(searchService).searchAndGetResponse(any(SearchRequestDto.class), eq("OR"));
    }

    @Test
    void testSearchWithInvalidOperator() throws Exception {
        mockMvc.perform(post("/api/v1/search")
                .param("operator", "INVALID")
                .contentType(MediaType.APPLICATION_JSON)
                .content(sampleJson))
            .andDo(print())
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Invalid operator. Must be either 'AND' or 'OR'"));

        // Verify the service was not called
        verify(searchService, never()).searchAndGetRawCatalog(any(), anyInt(), anyInt(), any());
    }

    @Test
    void testSearchWithInvalidRequest() throws Exception {
        doThrow(new IllegalArgumentException("Invalid request")).when(requestValidator).validate(any());

        mockMvc.perform(post("/api/v1/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(sampleJson))
            .andDo(print())
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Invalid request: Invalid request"));
    }

    @Test
    void testSearchWithError() throws Exception {
        doNothing().when(requestValidator).validate(any());
        when(searchService.searchAndGetRawCatalog(any(), anyInt(), anyInt(), any(SearchQueryBuilder.LogicalOperator.class)))
            .thenThrow(new IOException("Search error"));

        mockMvc.perform(post("/api/v1/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(sampleJson))
            .andDo(print())
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.error").value("Search service temporarily unavailable"));
    }
} 