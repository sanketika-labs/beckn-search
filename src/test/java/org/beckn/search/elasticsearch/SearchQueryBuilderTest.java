package org.beckn.search.elasticsearch;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.beckn.search.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Files;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SearchQueryBuilderTest {
    private SearchQueryBuilder queryBuilder;
    private ObjectMapper objectMapper;
    private String sampleJson;
    private SearchRequestDto request;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        queryBuilder = new SearchQueryBuilder(objectMapper);
        
        ClassPathResource resource = new ClassPathResource("search_intent_body_sample.json");
        sampleJson = new String(Files.readAllBytes(resource.getFile().toPath()));
        
        request = createSampleRequest();
    }

    @Test
    void testBuildSearchQuery() throws Exception {
        SearchRequestDto request = objectMapper.readValue(sampleJson, SearchRequestDto.class);
        Query query = queryBuilder.buildSearchQuery(request, SearchQueryBuilder.LogicalOperator.OR);
        
        assertNotNull(query, "Query should not be null");
        assertNotNull(query.bool(), "Query should be a boolean query");
        assertFalse(query.bool().should().isEmpty(), "Query should have at least one should clause");
    }

    @Test
    void testBuildSearchQueryWithEmptyRequest() {
        SearchRequestDto emptyRequest = new SearchRequestDto();
        Query query = queryBuilder.buildSearchQuery(emptyRequest, SearchQueryBuilder.LogicalOperator.AND);
        
        assertNotNull(query, "Query should not be null even for empty request");
        assertNotNull(query.matchAll(), "Query should be a match_all query");
    }

    @Test
    void testBuildSearchQueryWithProvider() {
        Provider provider = new Provider();
        provider.setId("test-provider");
        
        Descriptor descriptor = new Descriptor();
        descriptor.setName("Test Provider");
        provider.setDescriptor(descriptor);
        
        request.getMessage().getIntent().setProvider(provider);

        Query query = queryBuilder.buildSearchQuery(request, SearchQueryBuilder.LogicalOperator.AND);
        assertNotNull(query);
        assertTrue(query.bool().must().size() > 0);
    }

    @Test
    void testBuildSearchQueryWithItems() {
        SearchRequestDto request = new SearchRequestDto();
        Context context = new Context();
        context.setDomain("test-domain");
        context.setTransactionId("txn-123");
        context.setMessageId("msg-123");
        context.setTimestamp("2024-03-15T10:00:00Z");
        request.setContext(context);

        Message message = new Message();
        Intent intent = new Intent();
        Item item = new Item();
        
        // Add a descriptor with multiple non-null fields
        Descriptor itemDescriptor = new Descriptor();
        itemDescriptor.setName("Test Product");
        itemDescriptor.setCode("TP001");
        itemDescriptor.setShortDesc("A test product");
        itemDescriptor.setLongDesc("A detailed description of the test product");
        item.setDescriptor(itemDescriptor);
        
        // Add a price with multiple non-null fields
        Price price = new Price();
        price.setValue("100");
        price.setCurrency("INR");
        price.setMinimumValue("90");
        price.setMaximumValue("110");
        item.setPrice(price);
        
        // Add location information
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
        
        // Add provider information
        Provider provider = new Provider();
        provider.setId("test-provider");
        Descriptor providerDesc = new Descriptor();
        providerDesc.setName("Test Provider");
        providerDesc.setCode("TP001");
        provider.setDescriptor(providerDesc);
        intent.setProvider(provider);
        
        intent.setItems(Arrays.asList(item));
        message.setIntent(intent);
        request.setMessage(message);

        Query query = queryBuilder.buildSearchQuery(request, SearchQueryBuilder.LogicalOperator.AND);
        assertNotNull(query);
        assertNotNull(query.bool());
        assertTrue(!query.bool().must().isEmpty(), "Query should have must clauses");
    }

    @Test
    void testBuildSearchQueryWithCategories() {
        Provider provider = new Provider();
        Category category = new Category();
        category.setId("test-category");
        provider.setCategories(Arrays.asList(category));
        
        // Add a descriptor with multiple non-null fields
        Descriptor providerDesc = new Descriptor();
        providerDesc.setName("Test Provider");
        providerDesc.setCode("TP001");
        providerDesc.setShortDesc("A test provider");
        providerDesc.setLongDesc("A detailed description of the test provider");
        provider.setDescriptor(providerDesc);
        
        // Add location information
        Location location = new Location();
        Location.Country country = new Location.Country();
        country.setName("Test Country");
        country.setCode("test-country");
        location.setCountry(country);
        
        Location.City city = new Location.City();
        city.setName("Test City");
        city.setCode("test-city");
        location.setCity(city);
        request.getContext().setLocation(location);
        
        request.getMessage().getIntent().setProvider(provider);

        Query query = queryBuilder.buildSearchQuery(request, SearchQueryBuilder.LogicalOperator.AND);
        assertNotNull(query);
        assertNotNull(query.bool());
        assertTrue(!query.bool().must().isEmpty(), "Query should have must clauses");
    }

    @Test
    void testBuildSearchQueryWithComplexFilters() {
        Provider provider = new Provider();
        provider.setId("test-provider");
        
        Descriptor providerDesc = new Descriptor();
        providerDesc.setName("Test Provider");
        providerDesc.setCode("TP001");
        provider.setDescriptor(providerDesc);
        
        Category category = new Category();
        category.setId("test-category");
        provider.setCategories(Arrays.asList(category));
        
        Item item = new Item();
        Descriptor itemDesc = new Descriptor();
        itemDesc.setName("Test Item");
        itemDesc.setCode("TI001");
        item.setDescriptor(itemDesc);
        
        Price price = new Price();
        price.setCurrency("INR");
        price.setValue("100");
        price.setMinimumValue("90");
        price.setMaximumValue("110");
        item.setPrice(price);
        
        request.getMessage().getIntent().setProvider(provider);
        request.getMessage().getIntent().setItems(Arrays.asList(item));

        Query query = queryBuilder.buildSearchQuery(request, SearchQueryBuilder.LogicalOperator.AND);
        assertNotNull(query);
        assertTrue(query.bool().must().size() > 0);
    }

    @Test
    void testBuildSearchQueryWithAndOperator() throws Exception {
        SearchRequestDto request = objectMapper.readValue(sampleJson, SearchRequestDto.class);
        Query query = queryBuilder.buildSearchQuery(request, SearchQueryBuilder.LogicalOperator.AND);
        
        assertNotNull(query, "Query should not be null");
        assertNotNull(query.bool(), "Query should be a boolean query");
        
        // Verify that we have must clauses instead of should clauses
        BoolQuery boolQuery = query.bool();
        assertFalse(boolQuery.must().isEmpty(), "Query should have must clauses");
        assertTrue(boolQuery.should().isEmpty(), "Query should not have should clauses");
    }

    @Test
    void testBuildSearchQueryWithOrOperator() throws Exception {
        SearchRequestDto request = objectMapper.readValue(sampleJson, SearchRequestDto.class);
        Query query = queryBuilder.buildSearchQuery(request, SearchQueryBuilder.LogicalOperator.OR);
        
        assertNotNull(query, "Query should not be null");
        assertNotNull(query.bool(), "Query should be a boolean query");
        
        // Verify that we have should clauses
        BoolQuery boolQuery = query.bool();
        assertFalse(boolQuery.should().isEmpty(), "Query should have should clauses");
        assertTrue(boolQuery.must().isEmpty(), "Query should not have must clauses for OR operator");
    }

    @Test
    void testBuildSearchQueryWithComplexFiltersAndAndOperator() {
        Provider provider = new Provider();
        provider.setId("test-provider");
        
        Descriptor providerDesc = new Descriptor();
        providerDesc.setName("Test Provider");
        providerDesc.setCode("TP001");
        provider.setDescriptor(providerDesc);
        
        Category category = new Category();
        category.setId("test-category");
        provider.setCategories(Arrays.asList(category));
        
        Item item = new Item();
        Descriptor itemDesc = new Descriptor();
        itemDesc.setName("Test Item");
        itemDesc.setCode("TI001");
        item.setDescriptor(itemDesc);
        
        Price price = new Price();
        price.setCurrency("INR");
        price.setValue("100");
        price.setMinimumValue("90");
        price.setMaximumValue("110");
        item.setPrice(price);
        
        request.getMessage().getIntent().setProvider(provider);
        request.getMessage().getIntent().setItems(Arrays.asList(item));

        Query query = queryBuilder.buildSearchQuery(request, SearchQueryBuilder.LogicalOperator.AND);
        assertNotNull(query);
        assertTrue(query.bool().must().size() > 0);
    }

    @Test
    void testBuildSearchQueryWithNullRequest() {
        SearchRequestDto emptyRequest = null;
        assertThrows(NullPointerException.class, () -> queryBuilder.buildSearchQuery(emptyRequest, SearchQueryBuilder.LogicalOperator.AND));
    }

    @Test
    void testBuildSearchQueryWithLocationFilters() {
        SearchRequestDto request = new SearchRequestDto();
        Context context = new Context();
        request.setContext(context);

        Message message = new Message();
        Intent intent = new Intent();
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
        message.setIntent(intent);
        request.setMessage(message);

        Query query = queryBuilder.buildSearchQuery(request, SearchQueryBuilder.LogicalOperator.AND);
        assertNotNull(query);
    }

    @Test
    void testBuildSearchQueryWithItemFilters() {
        SearchRequestDto request = new SearchRequestDto();
        Context context = new Context();
        request.setContext(context);

        Message message = new Message();
        Intent intent = new Intent();
        Item item = new Item();
        Descriptor itemDescriptor = new Descriptor();
        itemDescriptor.setName("Test Product");
        item.setDescriptor(itemDescriptor);
        intent.setItems(Arrays.asList(item));
        message.setIntent(intent);
        request.setMessage(message);

        Query query = queryBuilder.buildSearchQuery(request, SearchQueryBuilder.LogicalOperator.AND);
        assertNotNull(query);
    }

    @Test
    void testFlattenFields() {
        SearchRequestDto request = new SearchRequestDto();
        Context context = new Context();
        context.setDomain("test-domain");
        request.setContext(context);

        Message message = new Message();
        Intent intent = new Intent();
        
        Item item = new Item();
        Descriptor itemDescriptor = new Descriptor();
        itemDescriptor.setName("Test Product");
        item.setDescriptor(itemDescriptor);
        intent.setItems(Arrays.asList(item));
        
        Provider provider = new Provider();
        Descriptor providerDescriptor = new Descriptor();
        providerDescriptor.setName("Test Provider");
        provider.setDescriptor(providerDescriptor);
        intent.setProvider(provider);
        
        message.setIntent(intent);
        request.setMessage(message);

        Map<String, Object> flattenedFields = queryBuilder.flattenFields("", request);
        assertNotNull(flattenedFields);
        assertFalse(flattenedFields.isEmpty());
    }

    private SearchRequestDto createSampleRequest() {
        SearchRequestDto request = new SearchRequestDto();
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
        
        Message message = new Message();
        Intent intent = new Intent();
        message.setIntent(intent);
        
        request.setContext(context);
        request.setMessage(message);
        
        return request;
    }
} 