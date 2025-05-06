package org.beckn.search.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.beckn.search.model.*;
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
import java.util.Arrays;
import java.util.HashMap;

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
    private static final String INDEX_NAME = "test-domain";
    private static boolean dataLoaded = false;
    private static SearchRequestDto searchRequest;

    @BeforeAll
    static void setUpClass() throws IOException {
        dataLoaded = false;
        
        // Initialize search request
        searchRequest = new SearchRequestDto();
        Context context = new Context();
        context.setDomain("test-domain");
        
        Location location = new Location();
        Location.Country country = new Location.Country();
        country.setName("Test Country");
        country.setCode("test-country");
        location.setCountry(country);
        
        Location.City city = new Location.City();
        city.setName("Test City");
        city.setCode("test-city");
        location.setCity(city);
        
        context.setLocation(location);
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
    void testResponseMapping() throws IOException {
        String rawCatalog = searchService.searchAndGetRawCatalog(searchRequest);
        
        assertNotNull(rawCatalog, "Raw catalog should not be null");
        assertNotEquals("[]", rawCatalog, "Raw catalog should not be empty");
        
        // Verify it's valid JSON
        JsonNode catalogNode = objectMapper.readTree(rawCatalog);
        assertTrue(catalogNode.isArray() || catalogNode.isObject(), "Raw catalog should be valid JSON array or object");
    }
    
    @Test
    void testSearchWithIntentFilters() throws IOException {
        // Create a search request with specific intent filters
        SearchRequestDto request = new SearchRequestDto();
        Context context = new Context();
        context.setDomain("test-domain");
        request.setContext(context);
        
        Message message = new Message();
        Intent intent = new Intent();
        
        // Add item filter
        Item item = new Item();
        Descriptor itemDescriptor = new Descriptor();
        itemDescriptor.setName("Test Product 2");
        item.setDescriptor(itemDescriptor);
        intent.setItems(Arrays.asList(item));
        
        // Add provider filter
        Provider provider = new Provider();
        Descriptor providerDescriptor = new Descriptor();
        providerDescriptor.setName("Provider 2");
        provider.setDescriptor(providerDescriptor);
        intent.setProvider(provider);
        
        message.setIntent(intent);
        request.setMessage(message);

        // Search and get response
        SearchResponseDto response = searchService.searchAndGetResponse(request, "OR");
        
        // Verify the response
        assertNotNull(response, "Response should not be null");
        assertNotNull(response.getMessage(), "Message should not be null");
        assertNotNull(response.getMessage().getCatalog(), "Catalog should not be null");
        assertNotNull(response.getMessage().getCatalog().getProviders(), "Providers should not be null");
        
        // Verify that we get at least one result
        JsonNode providers = response.getMessage().getCatalog().getProviders();
        assertTrue(providers.isArray() || providers.isObject(), "Providers should be either an array or object");
        
        // If it's an array, verify we have at least one element
        if (providers.isArray()) {
            assertTrue(providers.size() > 0, "Should return at least one result");
        }
    }

    @Test
    void testDomainWithColon() throws IOException {
        // Create a search request with domain containing colon
        SearchRequestDto request = new SearchRequestDto();
        Context context = new Context();
        context.setDomain("test:domain");
        request.setContext(context);
        
        Message message = new Message();
        Intent intent = new Intent();
        message.setIntent(intent);
        request.setMessage(message);

        // First request should fail as index doesn't exist
        assertThrows(IllegalArgumentException.class, () -> searchService.searchAndGetResponse(request, 0, 10, SearchQueryBuilder.LogicalOperator.AND));

        // Create index with hyphenated name
        String indexName = "test-domain";
        createIndex(indexName);
        indexTestData(indexName);

        // Now search should work
        SearchResponseDto response = searchService.searchAndGetResponse(request, 0, 10, SearchQueryBuilder.LogicalOperator.AND);
        assertNotNull(response);
        assertNotNull(response.getMessage());
        assertNotNull(response.getMessage().getCatalog());
        assertNotNull(response.getMessage().getCatalog().getProviders());
    }

    private void createIndex(String indexName) throws IOException {
        if (!elasticsearchClient.indices().exists(e -> e.index(indexName)).value()) {
            elasticsearchClient.indices().create(c -> c.index(indexName));
        }
    }

    private void indexTestData(String indexName) throws IOException {
        // Create test data
        BulkRequest.Builder br = new BulkRequest.Builder();
        
        // Add test documents
        Map<String, Object> doc1 = new HashMap<>();
        doc1.put("provider_descriptor_name", "Provider 1");
        doc1.put("items_descriptor_name", Arrays.asList("Test Product 1"));
        doc1.put("raw_catalog", "{\"message\":{\"catalog\":{\"descriptor\":{\"name\":\"Test Catalog 1\"},\"providers\":[{\"id\":\"provider1\",\"descriptor\":{\"name\":\"Provider 1\"},\"items\":[{\"id\":\"test-product-1\",\"descriptor\":{\"name\":\"Test Product 1\"},\"price\":{\"value\":\"24\",\"currency\":\"USD\"}}]}]}}}");

        Map<String, Object> doc2 = new HashMap<>();
        doc2.put("provider_descriptor_name", "Provider 2");
        doc2.put("items_descriptor_name", Arrays.asList("Test Product 2"));
        doc2.put("raw_catalog", "{\"message\":{\"catalog\":{\"descriptor\":{\"name\":\"Test Catalog 2\"},\"providers\":[{\"id\":\"provider2\",\"descriptor\":{\"name\":\"Provider 2\"},\"items\":[{\"id\":\"test-product-2\",\"descriptor\":{\"name\":\"Test Product 2\"},\"price\":{\"value\":\"48\",\"currency\":\"USD\"}}]}]}}}");

        br.operations(op -> op
            .index(i -> i
                .index(indexName)
                .id("1")
                .document(doc1)
            )
        ).operations(op -> op
            .index(i -> i
                .index(indexName)
                .id("2")
                .document(doc2)
            )
        );

        elasticsearchClient.bulk(br.build());
        elasticsearchClient.indices().refresh(r -> r.index(indexName));
    }
} 