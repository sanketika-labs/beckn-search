package org.beckn.search.performance;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.beckn.search.api.SearchController;
import org.beckn.search.elasticsearch.SearchService;
import org.beckn.search.model.SearchRequestDto;
import org.beckn.search.model.SearchResponseDto;
import org.beckn.search.validation.SearchRequestValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Disabled("Disable Search Performance Test")
@WebMvcTest(SearchController.class)
class SearchPerformanceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SearchService searchService;

    @MockBean
    private SearchRequestValidator requestValidator;

    private String sampleJson;
    private String complexJson;
    private SearchResponseDto mockResponse;

    @BeforeEach
    void setUp() throws Exception {
        ClassPathResource resource = new ClassPathResource("search_intent_body_sample.json");
        sampleJson = new String(Files.readAllBytes(resource.getFile().toPath()));

        // Create a complex query with multiple filters
        complexJson = """
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
                                {"id": "food"},
                                {"id": "beverages"}
                            ]
                        },
                        "items": [
                            {
                                "descriptor": {
                                    "name": "milk",
                                    "code": "milk-001"
                                },
                                "price": {
                                    "currency": "INR",
                                    "value": "50",
                                    "minimum_value": "45",
                                    "maximum_value": "55"
                                }
                            },
                            {
                                "descriptor": {
                                    "name": "bread",
                                    "code": "bread-001"
                                },
                                "price": {
                                    "currency": "INR",
                                    "value": "30",
                                    "minimum_value": "25",
                                    "maximum_value": "35"
                                }
                            }
                        ]
                    }
                }
            }
            """;

        // Setup mock response
        String catalogJson = """
            {
                "context": {
                    "domain": "retail",
                    "country": "IND",
                    "city": "std:080",
                    "timestamp": "2025-04-15T10:30:00Z"
                },
                "message": {
                    "catalog": {
                        "descriptor": {
                            "name": "Test Catalog",
                            "code": "test-catalog"
                        },
                        "providers": [
                            {
                                "id": "P1",
                                "descriptor": {
                                    "name": "Test Provider"
                                },
                                "items": [
                                    {
                                        "id": "I1",
                                        "descriptor": {
                                            "name": "milk",
                                            "code": "milk-001"
                                        },
                                        "price": {
                                            "currency": "INR",
                                            "value": "50"
                                        }
                                    },
                                    {
                                        "id": "I2",
                                        "descriptor": {
                                            "name": "bread",
                                            "code": "bread-001"
                                        },
                                        "price": {
                                            "currency": "INR",
                                            "value": "30"
                                        }
                                    }
                                ]
                            }
                        ]
                    }
                }
            }
            """;

        mockResponse = objectMapper.readValue(catalogJson, SearchResponseDto.class);
        
        // Mock service response
        when(searchService.searchAndGetResponse(any(SearchRequestDto.class), eq("OR")))
            .thenReturn(mockResponse);
    }

    @Test
    void testResponseTime() throws Exception {
        long startTime = System.currentTimeMillis();
        
        MvcResult result = mockMvc.perform(post("/api/v1/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(sampleJson))
            .andExpect(status().isOk())
            .andReturn();
            
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        // Response time should be under 3000ms in test environment
        assertTrue(duration < 3000, "Response time exceeded 3000ms: " + duration + "ms");
    }

    @Test
    void testConcurrentRequests() throws Exception {
        int numThreads = 10;
        int requestsPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        List<CompletableFuture<Long>> futures = new ArrayList<>();

        for (int i = 0; i < numThreads; i++) {
            CompletableFuture<Long> future = CompletableFuture.supplyAsync(() -> {
                try {
                    long totalDuration = 0;
                    for (int j = 0; j < requestsPerThread; j++) {
                        long startTime = System.currentTimeMillis();
                        
                        mockMvc.perform(post("/api/v1/search")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(sampleJson))
                            .andExpect(status().isOk());
                            
                        long endTime = System.currentTimeMillis();
                        totalDuration += (endTime - startTime);
                    }
                    return totalDuration / requestsPerThread;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, executor);
            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(30, TimeUnit.SECONDS);
        
        double averageResponseTime = futures.stream()
            .mapToLong(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            })
            .average()
            .orElse(0.0);
            
        assertTrue(averageResponseTime < 1000, 
            "Average response time exceeded 1000ms: " + averageResponseTime + "ms");

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void testLargeResultSet() throws Exception {
        long startTime = System.currentTimeMillis();
        
        MvcResult result = mockMvc.perform(post("/api/v1/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(complexJson))
            .andExpect(status().isOk())
            .andReturn();
            
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        // Response time should be under 3000ms in test environment
        assertTrue(duration < 3000, "Response time exceeded 3000ms: " + duration + "ms");
        
        String response = result.getResponse().getContentAsString();
        assertTrue(response.length() > 0, "Response should not be empty");
    }

    @Test
    void testComplexQueries() throws Exception {
        long startTime = System.currentTimeMillis();
        
        MvcResult result = mockMvc.perform(post("/api/v1/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(complexJson))
            .andExpect(status().isOk())
            .andReturn();
            
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        // Response time should be under 3000ms for complex queries
        assertTrue(duration < 3000, "Response time exceeded 3000ms: " + duration + "ms");
        
        String response = result.getResponse().getContentAsString();
        assertTrue(response.contains("milk") && response.contains("bread"), 
            "Response should contain all queried items");
    }
} 