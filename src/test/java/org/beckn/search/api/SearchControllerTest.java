package org.beckn.search.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.beckn.search.elasticsearch.SearchService;
import org.beckn.search.model.*;
import org.beckn.search.transformer.SearchResponseTransformer;
import org.beckn.search.validation.SearchRequestValidator;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
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
    private SearchResponseTransformer responseTransformer;

    @MockBean
    private SearchRequestValidator requestValidator;

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

    private SearchResponseDto createMockResponse(String catalogName, String providerName) throws IOException {
        SearchResponseDto response = new SearchResponseDto();
        
        // Set context
        Context context = new Context();
        context.setDomain("retail");
        context.setCountry("IND");
        context.setCity("std:080");
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
        
        // Set providers if provided
        if (providerName != null) {
            String providersJson = """
                [
                    {
                        "id": "provider-1",
                        "descriptor": {
                            "name": "%s",
                            "code": "PRV-001",
                            "short_desc": "Local retail store",
                            "long_desc": "Your neighborhood store for daily needs"
                        },
                        "categories": [
                            {
                                "id": "grocery",
                                "descriptor": {
                                    "name": "Grocery",
                                    "code": "CAT-001"
                                }
                            }
                        ],
                        "items": [
                            {
                                "id": "item-1",
                                "descriptor": {
                                    "name": "Milk",
                                    "code": "MLK-001",
                                    "short_desc": "Fresh milk",
                                    "long_desc": "Farm fresh milk"
                                },
                                "price": {
                                    "currency": "INR",
                                    "value": "50.00"
                                }
                            }
                        ]
                    }
                ]
                """.formatted(providerName);
            catalog.setProviders(objectMapper.readTree(providersJson));
        }
        
        message.setCatalog(catalog);
        response.setMessage(message);
        
        return response;
    }

    @Test
    void testSuccessfulSearch() throws Exception {
        String requestJson = loadTestFile("search_intent_body_sample.json");
        String catalogJson = """
            {
                "message": {
                    "catalog": {
                        "descriptor": {
                            "name": "EcoCharge-Retail-Catalog",
                            "code": "CATALOG-001",
                            "short_desc": "Retail catalog",
                            "long_desc": "Comprehensive retail catalog with multiple providers"
                        },
                        "providers": [
                            {
                                "id": "provider-1",
                                "descriptor": {
                                    "name": "Local Store",
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
                    }
                }
            }
            """;

        SearchResponseDto mockResponse = createMockResponse("EcoCharge-Retail-Catalog", "Local Store");

        // Mock validator to do nothing (validation passes)
        doNothing().when(requestValidator).validate(any(SearchRequestDto.class));
        
        when(searchService.searchAndGetRawCatalog(any(SearchRequestDto.class), eq(0), eq(10))).thenReturn(catalogJson);
        when(responseTransformer.transformToResponse(catalogJson)).thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.context.domain").value("retail"))
            .andExpect(jsonPath("$.context.country").value("IND"))
            .andExpect(jsonPath("$.context.city").value("std:080"))
            .andExpect(jsonPath("$.message.catalog.descriptor.name").value("EcoCharge-Retail-Catalog"))
            .andExpect(jsonPath("$.message.catalog.descriptor.code").value("CATALOG-001"))
            .andExpect(jsonPath("$.message.catalog.providers[0].id").value("provider-1"))
            .andExpect(jsonPath("$.message.catalog.providers[0].descriptor.name").value("Local Store"))
            .andExpect(jsonPath("$.message.catalog.providers[0].categories[0].id").value("grocery"));
    }

    @Test
    void testSearchWithInvalidRequest() throws Exception {
        String invalidJson = """
            {
                "context": {},
                "message": {}
            }
            """;

        mockMvc.perform(post("/api/v1/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
            .andExpect(status().isBadRequest());
    }

    @Test
    void testSearchWithVariousFilters() throws Exception {
        String requestJson = """
            {
                "context": {
                    "domain": "retail",
                    "country": "IND",
                    "city": "std:080",
                    "bap_id": "test",
                    "bap_uri": "test",
                    "transaction_id": "test",
                    "message_id": "test",
                    "timestamp": "2025-04-15T10:30:00Z"
                },
                "message": {
                    "intent": {
                        "provider": {
                            "categories": [
                                {"id": "grocery"},
                                {"id": "food"}
                            ]
                        },
                        "items": [
                            {
                                "descriptor": {
                                    "name": "milk"
                                },
                                "price": {
                                    "currency": "INR",
                                    "value": "50"
                                }
                            }
                        ]
                    }
                }
            }
            """;

        String catalogJson = """
            {
                "message": {
                    "catalog": {
                        "descriptor": {
                            "name": "EcoCharge-Retail-Catalog",
                            "code": "CATALOG-001",
                            "short_desc": "Filtered catalog",
                            "long_desc": "Catalog filtered by category and items"
                        },
                        "providers": [
                            {
                                "id": "provider-1",
                                "descriptor": {
                                    "name": "Grocery Store",
                                    "code": "PRV-001"
                                },
                                "categories": [
                                    {
                                        "id": "grocery",
                                        "descriptor": {
                                            "name": "Grocery",
                                            "code": "CAT-001"
                                        }
                                    },
                                    {
                                        "id": "food",
                                        "descriptor": {
                                            "name": "Food",
                                            "code": "CAT-002"
                                        }
                                    }
                                ],
                                "items": [
                                    {
                                        "id": "item-1",
                                        "descriptor": {
                                            "name": "milk",
                                            "code": "MLK-001"
                                        },
                                        "price": {
                                            "currency": "INR",
                                            "value": "50.00"
                                        }
                                    }
                                ]
                            }
                        ]
                    }
                }
            }
            """;
        
        SearchResponseDto mockResponse = createMockResponse("EcoCharge-Retail-Catalog", "Grocery Store");

        // Mock validator to do nothing (validation passes)
        doNothing().when(requestValidator).validate(any(SearchRequestDto.class));
        
        when(searchService.searchAndGetRawCatalog(any(SearchRequestDto.class), eq(0), eq(10))).thenReturn(catalogJson);
        when(responseTransformer.transformToResponse(catalogJson)).thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.context.domain").value("retail"))
            .andExpect(jsonPath("$.message.catalog.descriptor.name").value("EcoCharge-Retail-Catalog"))
            .andExpect(jsonPath("$.message.catalog.providers[0].descriptor.name").value("Grocery Store"))
            .andExpect(jsonPath("$.message.catalog.providers[0].categories[0].id").value("grocery"))
            .andExpect(jsonPath("$.message.catalog.providers[0].items[0].descriptor.name").value("Milk"))
            .andExpect(jsonPath("$.message.catalog.providers[0].items[0].price.value").value("50.00"));
    }

    @Test
    void testSearchWithError() throws Exception {
        String requestJson = loadTestFile("search_intent_body_sample.json");

        when(searchService.searchAndGetRawCatalog(any(SearchRequestDto.class), any(Integer.class), any(Integer.class)))
            .thenThrow(new RuntimeException("Search failed"));

        mockMvc.perform(post("/api/v1/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.error").exists())
            .andExpect(jsonPath("$.error").value("Internal server error"));
    }

    @Test
    void testSearchWithPagination() throws Exception {
        // Create request with pagination
        SearchRequestDto request = createRequestWithPagination(1, 20);
        String requestJson = objectMapper.writeValueAsString(request);
        String catalogJson = loadTestFile("beckn_catalog.json");
        
        SearchResponseDto mockResponse = createMockResponse("EcoCharge-Retail-Catalog", "Paginated Store");

        // Mock validator to do nothing (validation passes)
        doNothing().when(requestValidator).validate(any(SearchRequestDto.class));
        
        when(searchService.searchAndGetRawCatalog(any(SearchRequestDto.class), eq(1), eq(20)))
            .thenReturn(catalogJson);
        when(responseTransformer.transformToResponse(catalogJson))
            .thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.context.domain").value("retail"))
            .andExpect(jsonPath("$.message.catalog.descriptor.name").value("EcoCharge-Retail-Catalog"))
            .andExpect(jsonPath("$.message.catalog.providers[0].descriptor.name").value("Paginated Store"));
    }

    @Test
    void testSearchWithInvalidPaginationParameters() throws Exception {
        // Test with negative page number
        SearchRequestDto request = createRequestWithPagination(-1, 20);
        String requestJson = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/api/v1/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isBadRequest());

        // Test with negative size
        request = createRequestWithPagination(0, -1);
        requestJson = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/api/v1/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isBadRequest());

        // Test with zero size
        request = createRequestWithPagination(0, 0);
        requestJson = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/api/v1/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isBadRequest());
    }

    @Test
    void testSearchWithEmptyPageResults() throws Exception {
        // Create request with high page number
        SearchRequestDto request = createRequestWithPagination(999, 10);
        String requestJson = objectMapper.writeValueAsString(request);
        
        String emptyJson = """
            {
                "message": {
                    "catalog": {
                        "descriptor": {
                            "name": "EcoCharge-Retail-Catalog",
                            "code": "CATALOG-001",
                            "short_desc": "Empty catalog",
                            "long_desc": "Catalog with no providers"
                        },
                        "providers": []
                    }
                }
            }
            """;
        
        SearchResponseDto emptyResponse = createMockResponse("EcoCharge-Retail-Catalog", null);

        // Mock validator to do nothing (validation passes)
        doNothing().when(requestValidator).validate(any(SearchRequestDto.class));
        
        when(searchService.searchAndGetRawCatalog(any(SearchRequestDto.class), eq(999), eq(10)))
            .thenReturn(emptyJson);
        when(responseTransformer.transformToResponse(emptyJson))
            .thenReturn(emptyResponse);

        mockMvc.perform(post("/api/v1/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.context.domain").value("retail"))
            .andExpect(jsonPath("$.message.catalog.descriptor.name").value("EcoCharge-Retail-Catalog"))
            .andExpect(jsonPath("$.message.catalog.providers").isEmpty());
    }
} 