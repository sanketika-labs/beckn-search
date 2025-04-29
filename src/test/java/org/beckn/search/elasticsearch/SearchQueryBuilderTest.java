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
        Query query = queryBuilder.buildSearchQuery(request);
        
        assertNotNull(query, "Query should not be null");
        assertNotNull(query.bool(), "Query should be a boolean query");
        assertFalse(query.bool().should().isEmpty(), "Query should have at least one should clause");
    }

    @Test
    void testBuildSearchQueryWithEmptyRequest() {
        SearchRequestDto emptyRequest = new SearchRequestDto();
        Query query = queryBuilder.buildSearchQuery(emptyRequest);
        
        assertNotNull(query, "Query should not be null even for empty request");
        assertNotNull(query.bool(), "Query should be a boolean query");
        assertTrue(query.bool().should().isEmpty(), "Query should have no should clauses for empty request");
    }

    @Test
    void testBuildSearchQueryWithProvider() {
        Provider provider = new Provider();
        provider.setId("test-provider");
        
        Descriptor descriptor = new Descriptor();
        descriptor.setName("Test Provider");
        provider.setDescriptor(descriptor);
        
        request.getMessage().getIntent().setProvider(provider);

        Query query = queryBuilder.buildSearchQuery(request);
        assertNotNull(query);
        assertTrue(query.bool().should().size() > 0);
    }

    @Test
    void testBuildSearchQueryWithItems() {
        Item item = new Item();
        Descriptor descriptor = new Descriptor();
        descriptor.setName("Test Item");
        item.setDescriptor(descriptor);
        
        Price price = new Price();
        price.setCurrency("INR");
        price.setValue("100");
        item.setPrice(price);
        
        request.getMessage().getIntent().setItems(Arrays.asList(item));

        Query query = queryBuilder.buildSearchQuery(request);
        assertNotNull(query);
        assertTrue(query.bool().should().size() > 0);
    }

    @Test
    void testBuildSearchQueryWithCategories() {
        Provider provider = new Provider();
        Category category = new Category();
        category.setId("test-category");
        provider.setCategories(Arrays.asList(category));
        
        request.getMessage().getIntent().setProvider(provider);

        Query query = queryBuilder.buildSearchQuery(request);
        assertNotNull(query);
        assertTrue(query.bool().should().size() > 0);
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

        Query query = queryBuilder.buildSearchQuery(request);
        assertNotNull(query);
        assertTrue(query.bool().should().size() > 0);
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