package org.beckn.service;

import co.elastic.clients.elasticsearch._types.GeoLocation;
import co.elastic.clients.elasticsearch._types.LatLonGeoLocation;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.json.JsonData;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.beckn.model.SearchRequest;
import org.beckn.service.impl.SearchServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;
import org.beckn.model.SearchDocument;

import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
    "beck.fulltext.search.columns=name,description,coffeeTypes"
})
@Testcontainers
class SearchServiceTest {

    @Container
    private static final ElasticsearchContainer elasticsearchContainer = new ElasticsearchContainer(
            DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.12.1")
                    .asCompatibleSubstituteFor("docker.elastic.co/elasticsearch/elasticsearch")
    )
    .withEnv("discovery.type", "single-node")
    .withEnv("xpack.security.enabled", "false")
    .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m");

    @Autowired
    private SearchServiceImpl searchService;

    @Autowired
    private ElasticsearchTemplate elasticsearchTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        // Delete index if exists
        if (elasticsearchTemplate.indexOps(IndexCoordinates.of("retail")).exists()) {
            elasticsearchTemplate.indexOps(IndexCoordinates.of("retail")).delete();
        }
        
        // Create index with mapping
        elasticsearchTemplate.indexOps(IndexCoordinates.of("retail")).create();
        
        // Add sample document
        SearchDocument doc1 = new SearchDocument();
        doc1.setId("1");
        doc1.setName("Third Wave Coffee");
        doc1.setDescription("Specialty coffee roaster and cafe");
        doc1.setCity("New York");
        doc1.setState("NY");
        doc1.setCoffeeTypes(Arrays.asList("Espresso", "Americano", "Latte"));
        doc1.setTags(Arrays.asList("coffee", "specialty", "roastery"));
        doc1.setLocation(new GeoPoint(40.758896, -73.985130));

        SearchDocument doc2 = new SearchDocument();
        doc2.setId("2");
        doc2.setName("Starbucks");
        doc2.setDescription("Global coffee chain");
        doc2.setCity("New York");
        doc2.setState("NY");
        doc2.setCoffeeTypes(Arrays.asList("Espresso", "Frappuccino", "Cold Brew"));
        doc2.setTags(Arrays.asList("coffee", "chain", "global"));
        doc2.setLocation(new GeoPoint(40.768896, -73.995130));

        SearchDocument doc3 = new SearchDocument();
        doc3.setId("3");
        doc3.setName("Cafe Coffee Day");
        doc3.setDescription("Indian coffee chain");
        doc3.setCity("New York");
        doc3.setState("NY");
        doc3.setCoffeeTypes(Arrays.asList("Filter Coffee", "Cappuccino"));
        doc3.setTags(Arrays.asList("coffee", "chain", "indian"));
        doc3.setLocation(new GeoPoint(40.748896, -73.975130));

        IndexQuery indexQuery1 = new IndexQueryBuilder()
            .withId(doc1.getId())
            .withObject(doc1)
            .build();
        elasticsearchTemplate.index(indexQuery1, IndexCoordinates.of("retail"));

        IndexQuery indexQuery2 = new IndexQueryBuilder()
            .withId(doc2.getId())
            .withObject(doc2)
            .build();
        elasticsearchTemplate.index(indexQuery2, IndexCoordinates.of("retail"));

        IndexQuery indexQuery3 = new IndexQueryBuilder()
            .withId(doc3.getId())
            .withObject(doc3)
            .build();
        elasticsearchTemplate.index(indexQuery3, IndexCoordinates.of("retail"));

        elasticsearchTemplate.indexOps(IndexCoordinates.of("retail")).refresh();
    }

    @Test
    void testTextSearch() throws Exception {
        var request = createSearchRequest();
        request.getRequest().getSearch().setText("Third Wave");
        
        var result = searchService.search(request);
        
        assertNotNull(result);
        assertEquals(1, result.size());
        Map<String, Object> resultDoc = objectMapper.readValue(result.get(0).toString(), new TypeReference<Map<String, Object>>(){});
        assertEquals("Third Wave Coffee", resultDoc.get("name"));
    }

    @Test
    void testGeoSearch() throws Exception {
        var request = createSearchRequest();
        var geoSpatial = new SearchRequest.GeoSpatial();
        var location = new SearchRequest.Location();
        location.setLat(40.758896);
        location.setLon(-73.985130);
        geoSpatial.setLocation(location);
        geoSpatial.setDistance("1");
        geoSpatial.setUnit("km");
        request.getRequest().getSearch().setGeoSpatial(geoSpatial);
        
        var result = searchService.search(request);
        
        assertNotNull(result);
        assertEquals(3, result.size());
        Map<String, Object> resultDoc = objectMapper.readValue(result.get(0).toString(), new TypeReference<Map<String, Object>>(){});
        assertNotNull(resultDoc.get("name"));
    }

    @Test
    void testFilterSearch() throws Exception {
        var request = createSearchRequest();
        var filter = new SearchRequest.Filter();
        filter.setType("and");
        
        var field1 = new SearchRequest.Field();
        field1.setName("coffeeTypes");
        field1.setOp("in");
        field1.setValue(List.of("Americano"));
        
        var field2 = new SearchRequest.Field();
        field2.setType("or");
        
        var field2_1 = new SearchRequest.Field();
        field2_1.setName("city");
        field2_1.setOp("eq");
        field2_1.setValue("Bangalore");
        
        var field2_2 = new SearchRequest.Field();
        field2_2.setName("state");
        field2_2.setOp("eq");
        field2_2.setValue("Karnataka");
        field2.setFields(List.of(field2_1, field2_2));
        
        filter.setFields(List.of(field1, field2));
        request.getRequest().getSearch().setFilters(List.of(filter));
        
        var result = searchService.search(request);
        
        assertNotNull(result);
        assertEquals(1, result.size());
        Map<String, Object> resultDoc = objectMapper.readValue(result.get(0).toString(), new TypeReference<Map<String, Object>>(){});
        assertEquals("Third Wave Coffee", resultDoc.get("name"));
        // Verify array fields
        List<String> coffeeTypes = (List<String>) resultDoc.get("coffeeTypes");
        assertNotNull(coffeeTypes);
        assertEquals(3, coffeeTypes.size());
        List<String> tags = (List<String>) resultDoc.get("tags");
        assertNotNull(tags);
        assertEquals(2, tags.size());
    }

    @Test
    void testFilterSearch1() throws Exception {
        var request = createSearchRequest();
        var filter = new SearchRequest.Filter();
        filter.setType("and");

        var field1 = new SearchRequest.Field();
        field1.setName("tags");
        field1.setOp("in");
        field1.setValue(List.of("coffee"));

        var field2 = new SearchRequest.Field();
        field2.setName("coffeeTypes");
        field2.setOp("in");
        field2.setValue(List.of("Americano"));

        filter.setFields(List.of(field1, field2));
        request.getRequest().getSearch().setFilters(List.of(filter));

        var result = searchService.search(request);

        assertNotNull(result);
        assertEquals(1, result.size());
        Map<String, Object> resultDoc = objectMapper.readValue(result.get(0).toString(), new TypeReference<Map<String, Object>>(){});
        assertEquals("Third Wave Coffee", resultDoc.get("name"));
        // Verify array fields
        List<String> coffeeTypes = (List<String>) resultDoc.get("coffeeTypes");
        assertNotNull(coffeeTypes);
        assertTrue(coffeeTypes.contains("Americano"));
        List<String> tags = (List<String>) resultDoc.get("tags");
        assertNotNull(tags);
        assertTrue(tags.contains("coffee"));
    }

    private SearchRequest createSearchRequest() {
        var request = new SearchRequest();
        request.setId("api.catalog.search");
        request.setVer("v1");
        request.setTs(OffsetDateTime.now());
        
        var params = new SearchRequest.Params();
        params.setMsgid(UUID.randomUUID());
        request.setParams(params);
        
        var context = new SearchRequest.Context();
        context.setDomain("retail");
        
        var search = new SearchRequest.Search();
        var page = new SearchRequest.Page();
        page.setFrom(0);
        page.setSize(10);
        search.setPage(page);
        
        var req = new SearchRequest.Request();
        req.setContext(context);
        req.setSearch(search);
        request.setRequest(req);
        
        return request;
    }
} 