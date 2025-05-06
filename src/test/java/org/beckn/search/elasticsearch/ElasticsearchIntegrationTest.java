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

    /*
    @BeforeEach
    void setUp() throws IOException {
        // Create index with mapping
        String mappingJson = "{\n" +
            "  \"mappings\": {\n" +
            "    \"properties\": {\n" +
            "      \"providers_locations_gps\": {\n" +
            "        \"type\": \"geo_point\"\n" +
            "      }\n" +
            "    }\n" +
            "  }\n" +
            "}";

        // Delete index if exists
        if (elasticsearchClient.indices().exists(e -> e.index("test-domain")).value()) {
            elasticsearchClient.indices().delete(d -> d.index("test-domain"));
        }
        
        // Create index with mapping
        elasticsearchClient.indices().create(c -> c
            .index("test-domain")
            .withJson(new StringReader(mappingJson))
        );
        
        // Index test data
        indexTestData("test-domain");
    }
    */


    @BeforeEach
    void setUp() throws IOException {

        // Delete index if exists
        if (elasticsearchClient.indices().exists(e -> e.index("test-domain")).value()) {
            elasticsearchClient.indices().delete(d -> d.index("test-domain"));
        }

        String mappingJson = loadResourceAsString("/elasticsearch/ev_catalog_index_mapping.json");
        // Create index with mapping
        elasticsearchClient.indices().create(c -> c
                .index("test-domain")
                .withJson(new StringReader(mappingJson))
        );

        // Index test data
        indexTestData("test-domain");
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
                    String mappingJson = loadResourceAsString("/elasticsearch/ev_catalog_index_mapping.json");
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
        // Create a search request that matches test data
        SearchRequestDto request = new SearchRequestDto();
        Context context = new Context();
        context.setDomain("test-domain");
        request.setContext(context);
        
        Message message = new Message();
        Intent intent = new Intent();
        
        // Add item filter that matches test data
        Item item = new Item();
        Descriptor itemDescriptor = new Descriptor();
        itemDescriptor.setName("Test Product 1");
        item.setDescriptor(itemDescriptor);
        intent.setItems(List.of(item));
        
        message.setIntent(intent);
        request.setMessage(message);

        String rawCatalog = searchService.searchAndGetRawCatalog(request);
        
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
        intent.setItems(List.of(item));
        
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

        // Search should work as colons are replaced with hyphens
        SearchResponseDto response = searchService.searchAndGetResponse(request);
        
        assertNotNull(response);
        assertNotNull(response.getMessage());
        assertNotNull(response.getMessage().getCatalog());
    }

    @Test
    void testProviderLocationAndFulfillmentFilters() throws IOException {
        // Create a search request with location and fulfillment filters
        SearchRequestDto request = new SearchRequestDto();
        
        // Set up context
        Context context = new Context();
        context.setDomain("test-domain");
        context.setTransactionId("12345678-aaaa-bbbb-cccc-1234567890ab");
        context.setMessageId("abcdef12-3456-7890-abcd-ef1234567890");
        context.setTimestamp("2025-04-15T10:30:00Z");
        request.setContext(context);
        
        // Add provider with location and fulfillment filters
        Message message = new Message();
        Intent intent = new Intent();
        
        Provider provider = new Provider();
        
        // Add location filter
        Location location = new Location();
        location.setGps("30.2672,-97.7431");
        provider.setLocations(List.of(location));
        
        // Add fulfillment filter
        Fulfillment fulfillment = new Fulfillment();
        fulfillment.setType("onsite");
        provider.setFulfillments(List.of(fulfillment));
        
        intent.setProviders(List.of(provider));
        message.setIntent(intent);
        request.setMessage(message);

        // Search and get response
        SearchResponseDto response = searchService.searchAndGetResponse(request);
        
        // Verify the response
        assertNotNull(response, "Response should not be null");
        assertNotNull(response.getMessage(), "Message should not be null");
        assertNotNull(response.getMessage().getCatalog(), "Catalog should not be null");
        assertNotNull(response.getMessage().getCatalog().getProviders(), "Providers should not be null");
        
        // Verify that we get exactly one result (doc3)
        JsonNode providers = response.getMessage().getCatalog().getProviders();
        assertTrue(providers.isArray() || providers.isObject(), "Providers should be either an array or object");
        
        // If it's an array, verify we have exactly one element
        if (providers.isArray()) {
            assertEquals(1, providers.size(), "Should return exactly one result");
            
            // Verify it's the EV charger provider
            JsonNode resultProvider = providers.get(0);
            assertEquals("ecocharge-austin-0", resultProvider.get("id").asText(), "Should match the EV charger provider ID");
            assertEquals("EcoCharge", resultProvider.get("descriptor").get("name").asText(), "Should match the EV charger provider name");
            
            // Verify the location matches
            JsonNode locations = resultProvider.get("locations");
            assertTrue(locations.isArray(), "Locations should be an array");
            assertEquals("30.2672,-97.7431", locations.get(0).get("gps").asText(), "GPS coordinates should match");
            
            // Verify the fulfillment matches
            JsonNode fulfillments = resultProvider.get("fulfillments");
            assertTrue(fulfillments.isArray(), "Fulfillments should be an array");
            assertEquals("onsite", fulfillments.get(0).get("type").asText(), "Fulfillment type should match");
        }
    }

    @Test
    void testProviderGeoDistanceSearch() throws Exception {
        // Create a search request with GPS coordinates
        SearchRequestDto request = new SearchRequestDto();
        Context context = new Context();
        context.setDomain("test-domain");

        // Add provider with location and fulfillment filters
        Message message = new Message();
        Intent intent = new Intent();

        Provider provider = new Provider();

        // Add location filter
        Location location = new Location();
        location.setGps("30.2672,-97.7431");
        provider.setLocations(List.of(location));

        intent.setProviders(List.of(provider));
        message.setIntent(intent);
        request.setMessage(message);

        request.setContext(context);
        request.setMessage(message);

        // Search with geo distance filter
        var response = searchService.searchAndGetResponse(request);
        
        assertNotNull(response);
        assertNotNull(response.getMessage());
        assertNotNull(response.getMessage().getCatalog());

        // Verify that we get exactly one result (doc3)
        JsonNode providers = response.getMessage().getCatalog().getProviders();
        assertTrue(providers.isArray() || providers.isObject(), "Providers should be either an array or object");

        // If it's an array, verify we have exactly one element
        if (providers.isArray()) {
            assertEquals(1, providers.size(), "Should return exactly one result");

            // Verify it's the EV charger provider
            JsonNode resultProvider = providers.get(0);
            assertEquals("EcoCharge", resultProvider.get("descriptor").get("name").asText(), "Should match the EV charger provider name");
        }
    }

    @Test
    void testContextGeoDistanceSearch() throws Exception {
        // Create a search request with GPS coordinates
        SearchRequestDto request = new SearchRequestDto();
        Context context = new Context();
        context.setDomain("test-domain");

        Location location = new Location();
        location.setGps("30.2672,-97.7431"); // Austin, TX coordinates
        context.setLocation(location);

        Message message = new Message();
        request.setContext(context);
        request.setMessage(message);

        // Search with geo distance filter
        var response = searchService.searchAndGetResponse(request);

        assertNotNull(response);
        assertNotNull(response.getMessage());
        assertNotNull(response.getMessage().getCatalog());

        // Verify that we get exactly one result (doc3)
        JsonNode providers = response.getMessage().getCatalog().getProviders();
        assertTrue(providers.isArray() || providers.isObject(), "Providers should be either an array or object");

        // If it's an array, verify we have exactly one element
        if (providers.isArray()) {
            assertEquals(1, providers.size(), "Should return exactly one result");

            // Verify it's the EV charger provider
            JsonNode resultProvider = providers.get(0);
            assertEquals("EcoCharge", resultProvider.get("descriptor").get("name").asText(), "Should match the EV charger provider name");
        }
    }

    /*
    @Test
    void testGeoBoundingBoxSearch() throws Exception {
        // Create a search request with GPS coordinates
        SearchRequestDto request = new SearchRequestDto();
        Context context = new Context();
        context.setDomain("test-domain");
        
        Location location = new Location();
        location.setGps("30.2672,-97.7431"); // Austin, TX coordinates
        context.setLocation(location);
        
        Message message = new Message();
        request.setContext(context);
        request.setMessage(message);

        // Search with geo bounding box filter
        var response = searchService.searchAndGetResponse(request);
        
        assertNotNull(response);
        assertNotNull(response.getMessage());
        assertNotNull(response.getMessage().getCatalog());
    }
    */

    private void createIndex(String indexName) throws IOException {
        if (!elasticsearchClient.indices().exists(e -> e.index(indexName)).value()) {
            elasticsearchClient.indices().create(c -> c.index(indexName));
        }
    }

    private void indexTestData(String indexName) throws IOException {
        // Create test data
        BulkRequest.Builder br = new BulkRequest.Builder();
        
        // Add test documents with flattened structure for better searching
        Map<String, Object> doc1 = new HashMap<>();
        doc1.put("context_domain", "test-domain");
        doc1.put("providers_id", "provider1");
        doc1.put("providers_descriptor_name", "Provider 1");
        doc1.put("items_descriptor_name", List.of("Test Product 1"));
        doc1.put("items_price_value", "24");
        doc1.put("items_price_currency", "USD");
        doc1.put("raw_catalog", "{\"message\":{\"catalog\":{\"descriptor\":{\"name\":\"Test Catalog 1\",\"code\":\"CAT1\"},\"providers\":[{\"id\":\"provider1\",\"descriptor\":{\"name\":\"Provider 1\"},\"items\":[{\"id\":\"test-product-1\",\"descriptor\":{\"name\":\"Test Product 1\"},\"price\":{\"value\":\"24\",\"currency\":\"USD\"}}]}]}}}");

        Map<String, Object> doc2 = new HashMap<>();
        doc2.put("context_domain", "test-domain");
        doc2.put("providers_id", "provider2");
        doc2.put("providers_descriptor_name", "Provider 2");
        doc2.put("items_descriptor_name", List.of("Test Product 2"));
        doc2.put("items_price_value", "48");
        doc2.put("items_price_currency", "USD");
        doc2.put("raw_catalog", "{\"message\":{\"catalog\":{\"descriptor\":{\"name\":\"Test Catalog 2\",\"code\":\"CAT2\"},\"providers\":[{\"id\":\"provider2\",\"descriptor\":{\"name\":\"Provider 2\"},\"items\":[{\"id\":\"test-product-2\",\"descriptor\":{\"name\":\"Test Product 2\"},\"price\":{\"value\":\"48\",\"currency\":\"USD\"}}]}]}}}");

        Map<String, Object> doc3 = new HashMap<>();
        doc3.put("context_domain", "test-domain");
        doc3.put("context_location_gps", "30.2672,-97.7431");  // Changed to string for geo_point
        doc3.put("providers_id", "ecocharge-austin-0");
        doc3.put("providers_descriptor_name", "EcoCharge");
        doc3.put("providers_locations_gps", "30.2672,-97.7431");  // Changed to string for geo_point
        doc3.put("providers_locations_address", "233 Main St, Austin, Texas");
        doc3.put("providers_locations_city_name", "Austin");
        doc3.put("providers_locations_state_name", "Texas");
        doc3.put("providers_locations_country_name", "USA");
        doc3.put("providers_locations_area_code", "73301");
        doc3.put("providers_fulfillments_type", List.of("onsite"));
        doc3.put("items_id", List.of("ecocharge-austin-0-item-0"));
        doc3.put("items_descriptor_name", List.of("150kW EV Charger"));
        doc3.put("items_descriptor_short_desc", List.of("150kW public-dc Charger"));
        doc3.put("items_price_value", List.of("24"));
        doc3.put("items_price_currency", List.of("USD"));
        doc3.put("items_quantity_available_count", List.of(1));
        doc3.put("items_category_ids", List.of("public-dc"));
        doc3.put("items_fulfillment_ids", List.of("ful-ecocharge-austin-0"));
        doc3.put("items_rating", List.of(2.9));
        doc3.put("items_tags_list_value", Arrays.asList("CCS", "CHAdeMO"));
        doc3.put("items_tags_list_descriptor_code", Arrays.asList("port_type", "port_type"));
        doc3.put("items_tags_list_descriptor_name", Arrays.asList("CCS", "CHAdeMO"));
        doc3.put("raw_catalog", "{\"message\":{\"catalog\":{\"descriptor\":{\"name\":\"EcoCharge-Retail-Catalog\",\"code\":\"CATALOG-001\",\"short_desc\":\"Retail catalog\",\"long_desc\":\"Comprehensive retail catalog with multiple providers\"},\"providers\":[{\"id\":\"ecocharge-austin-0\",\"descriptor\":{\"name\":\"EcoCharge\"},\"locations\":[{\"gps\":\"30.2672,-97.7431\",\"address\":\"233 Main St, Austin, Texas\",\"city\":{\"name\":\"Austin\"},\"state\":{\"name\":\"Texas\"},\"country\":{\"name\":\"USA\"},\"area_code\":\"73301\"}],\"fulfillments\":[{\"type\":\"onsite\"}],\"items\":[{\"id\":\"ecocharge-austin-0-item-0\",\"descriptor\":{\"name\":\"150kW EV Charger\",\"short_desc\":\"150kW public-dc Charger\"},\"price\":{\"value\":\"24\",\"currency\":\"USD\"},\"quantity\":{\"available\":{\"count\":1}},\"category_ids\":[\"public-dc\"],\"fulfillment_ids\":[\"ful-ecocharge-austin-0\"],\"rating\":2.9,\"tags\":[{\"list\":[{\"value\":\"CCS\",\"descriptor\":{\"code\":\"port_type\",\"name\":\"CCS\"}},{\"value\":\"CHAdeMO\",\"descriptor\":{\"code\":\"port_type\",\"name\":\"CHAdeMO\"}}]}]}]}]}}}");

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
        ).operations(op -> op
            .index(i -> i
                .index(indexName)
                .id("3")
                .document(doc3)
            )
        );

        BulkResponse response = elasticsearchClient.bulk(br.build());
        if (response.errors()) {
            throw new RuntimeException("Failed to index test data: " + response.items().get(0).error().reason());
        }
        elasticsearchClient.indices().refresh(r -> r.index(indexName));
    }
} 