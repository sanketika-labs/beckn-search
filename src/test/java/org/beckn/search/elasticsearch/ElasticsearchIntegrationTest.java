package org.beckn.search.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.beckn.search.model.SearchRequestDto;
import org.beckn.search.model.Context;
import org.beckn.search.model.Message;
import org.beckn.search.model.Intent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.time.Duration;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ElasticsearchIntegrationTest {

    @Container
    private static final ElasticsearchContainer elasticsearchContainer = new ElasticsearchContainer(
            DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.12.1")
                    .asCompatibleSubstituteFor("docker.elastic.co/elasticsearch/elasticsearch")
    )
            .withEnv("discovery.type", "single-node")
            .withEnv("xpack.security.enabled", "false")
            .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")
            .waitingFor(
                Wait.forLogMessage(".*started.*", 1)
                    .withStartupTimeout(Duration.ofMinutes(5))
            );

    @DynamicPropertySource
    static void elasticsearchProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.elasticsearch.uris", elasticsearchContainer::getHttpHostAddress);
        registry.add("spring.elasticsearch.username", () -> "elastic");
        registry.add("spring.elasticsearch.password", () -> "changeme");
    }

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    @Autowired
    private ElasticsearchTemplate elasticsearchTemplate;

    @Autowired
    private SearchService searchService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;
    private static final String INDEX_NAME = "test_catalog";
    private static boolean dataLoaded = false;
    private static SearchRequestDto searchRequest;

    @BeforeAll
    static void setUpClass() throws IOException {
        dataLoaded = false;
        
        // Initialize search request
        searchRequest = new SearchRequestDto();
        Context context = new Context();
        context.setDomain(INDEX_NAME);
        context.setCountry("test-country");
        context.setCity("test-city");
        context.setBapId("test-bap");
        context.setBapUri("test-uri");
        context.setTransactionId("test-txn");
        context.setMessageId("test-msg");
        context.setTimestamp("2024-03-15T10:00:00Z");
        
        Message message = new Message();
        Intent intent = new Intent();
        message.setIntent(intent);
        
        searchRequest.setContext(context);
        searchRequest.setMessage(message);
    }

    @BeforeEach
    void setUp() throws Exception {
        if (!dataLoaded) {
            try {
                createIndexWithRetry();
                loadTestDataWithRetry();
                dataLoaded = true;
            } catch (Exception e) {
                System.err.println("Failed to set up test data: " + e.getMessage());
                System.err.println("Container logs: " + elasticsearchContainer.getLogs());
                throw e;
            }
        }
    }

    private void createIndexWithRetry() throws Exception {
        int retryCount = 0;
        Exception lastException = null;
        
        while (retryCount < MAX_RETRIES) {
            try {
                boolean indexExists = elasticsearchClient.indices().exists(builder -> 
                    builder.index(INDEX_NAME)
                ).value();
                
                if (!indexExists) {
                    String mappingJson = loadResourceAsString("/elasticsearch/mapping.json");
                    elasticsearchClient.indices().create(builder -> 
                        builder.index(INDEX_NAME)
                        .withJson(new StringReader(mappingJson))
                    );
                }
                return;
            } catch (Exception e) {
                lastException = e;
                retryCount++;
                if (retryCount < MAX_RETRIES) {
                    Thread.sleep(RETRY_DELAY_MS);
                }
            }
        }
        throw new RuntimeException("Failed to create index after " + MAX_RETRIES + " attempts", lastException);
    }

    private void loadTestDataWithRetry() throws Exception {
        int retryCount = 0;
        Exception lastException = null;
        
        while (retryCount < MAX_RETRIES) {
            try {
                String catalogJson = loadResourceAsString("/elasticsearch/test_data.json");
                List<Map<String, Object>> testData = objectMapper.readValue(
                    catalogJson,
                    new TypeReference<List<Map<String, Object>>>() {}
                );

                BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
                
                for (Map<String, Object> document : testData) {
                    bulkBuilder.operations(op -> op
                        .index(idx -> idx
                            .index(INDEX_NAME)
                            .document(document)
                        )
                    );
                }

                BulkResponse bulkResponse = elasticsearchClient.bulk(bulkBuilder.build());
                
                if (bulkResponse.errors()) {
                    throw new RuntimeException("Bulk indexing failed: " + bulkResponse.items().get(0).error().reason());
                }
                
                // Refresh the index to make documents immediately available
                elasticsearchClient.indices().refresh(r -> r.index(INDEX_NAME));
                return;
            } catch (Exception e) {
                lastException = e;
                retryCount++;
                if (retryCount < MAX_RETRIES) {
                    Thread.sleep(RETRY_DELAY_MS);
                }
            }
        }
        throw new RuntimeException("Failed to load test data after " + MAX_RETRIES + " attempts", lastException);
    }

    private String loadResourceAsString(String resourcePath) throws IOException {
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void testElasticsearchConnection() throws IOException {
        assertTrue(elasticsearchClient.ping().value(), "Elasticsearch is not available");
    }

    @Test
    void testIndexExists() throws IOException {
        assertTrue(elasticsearchClient.indices().exists(builder -> builder.index(INDEX_NAME)).value(), 
            "Index should exist after setup");
    }

    @Test
    void testSearchExecution() throws IOException {
        SearchResponse<Map> response = searchService.search(searchRequest);
        
        assertNotNull(response, "Search response should not be null");
        assertTrue(response.hits().total().value() > 0, "Should find matching documents");
    }

    @Test
    void testResponseMapping() throws IOException {
        SearchResponse<Map> response = searchService.search(searchRequest);
        
        assertNotNull(response, "Search response should not be null");
        assertTrue(response.hits().total().value() > 0, "Should find matching documents");
        
        response.hits().hits().forEach(hit -> {
            assertNotNull(hit.source(), "Document source should not be null");
            assertTrue(hit.source().containsKey("name"), "Document should have a name field");
            assertTrue(hit.source().containsKey("description"), "Document should have a description field");
        });
    }
} 