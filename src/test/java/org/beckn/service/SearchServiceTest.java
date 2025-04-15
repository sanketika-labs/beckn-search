package org.beckn.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.beckn.model.SearchDocument;
import org.beckn.model.SearchRequest;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;
import org.beckn.service.impl.SearchServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

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

    @DynamicPropertySource
    static void elasticsearchProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.elasticsearch.uris", elasticsearchContainer::getHttpHostAddress);
        registry.add("spring.elasticsearch.username", () -> "elastic");
        registry.add("spring.elasticsearch.password", () -> "changeme");
    }

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
        elasticsearchTemplate.indexOps(SearchDocument.class).create();
        elasticsearchTemplate.indexOps(SearchDocument.class).putMapping();

        // Add sample document
        SearchDocument doc1 = new SearchDocument();
        doc1.setId("1");
        doc1.setName("Third Wave Coffee");
        doc1.setDescription("Specialty coffee roaster and cafe");
        doc1.setCity("Bangalore");
        doc1.setState("Karnataka");
        doc1.setCoffeeTypes(Arrays.asList("Espresso", "Americano", "Latte"));
        doc1.setTags(Arrays.asList("coffee", "specialty", "roastery"));
        doc1.setLocation(new GeoPoint(40.758896, -73.985130));
        elasticsearchTemplate.save(doc1);

        SearchDocument doc2 = new SearchDocument();
        doc2.setId("2");
        doc2.setName("Starbucks");
        doc2.setDescription("Global coffee chain");
        doc2.setCity("Hyderabad");
        doc2.setState("Telangana");
        doc2.setCoffeeTypes(Arrays.asList("Espresso", "Frappuccino", "Cold Brew"));
        doc2.setTags(Arrays.asList("coffee", "chain", "global"));
        doc2.setLocation(new GeoPoint(40.768896, -73.995130));
        elasticsearchTemplate.save(doc2);

        SearchDocument doc3 = new SearchDocument();
        doc3.setId("3");
        doc3.setName("Cafe Coffee Day");
        doc3.setDescription("Indian coffee chain");
        doc3.setCity("Chennai");
        doc3.setState("Tamil Nadu");
        doc3.setCoffeeTypes(Arrays.asList("Filter Coffee", "Cappuccino"));
        doc3.setTags(Arrays.asList("coffee", "chain", "indian"));
        doc3.setLocation(new GeoPoint(40.748896, -73.975130));
        elasticsearchTemplate.save(doc3);

        elasticsearchTemplate.indexOps(IndexCoordinates.of("retail")).refresh();
    }

    @Test
    void testTextSearch() throws Exception {
        var request = createSearchRequest();
        request.getRequest().getSearch().setText("Third Wave");
        
        var response = searchService.search(request);
        
        System.out.println("Response: " + objectMapper.writeValueAsString(response));
        
        assertNotNull(response);
        assertEquals(request.getId(), response.getId());
        assertEquals(request.getVer(), response.getVer());
        assertEquals(request.getParams().getMsgid().toString(), response.getParams().getMsgid());
        assertEquals("SUCCESS", response.getParams().getStatus());
        assertEquals("OK", response.getResponseCode());
        assertEquals(1, response.getResult().size());
        assertNull(response.getError());
        
        Map<String, Object> resultDoc = response.getResult().get(0);
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
        
        var response = searchService.search(request);
        
        System.out.println("Response: " + objectMapper.writeValueAsString(response));
        System.out.println("Result size: " + response.getResult().size());
        
        assertNotNull(response);
        assertEquals(request.getId(), response.getId());
        assertEquals(request.getVer(), response.getVer());
        assertEquals(request.getParams().getMsgid().toString(), response.getParams().getMsgid());
        assertEquals("SUCCESS", response.getParams().getStatus());
        assertEquals("OK", response.getResponseCode());
        assertEquals(1, response.getResult().size());
        assertNull(response.getError());

        Map<String, Object> resultDoc = response.getResult().get(0);
        assertEquals("Third Wave Coffee", resultDoc.get("name"));
    }

    @Test
    void testFilterComplexSearch() throws Exception {
        var request = createSearchRequest();
        var filter = new SearchRequest.Filter();
        filter.setType("or");
        
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
        field2_2.setName("city");
        field2_2.setOp("eq");
        field2_2.setValue("chennai");
        field2.setFields(List.of(field2_1, field2_2));
        
        filter.setFields(List.of(field1, field2));
        request.getRequest().getSearch().setFilters(List.of(filter));
        
        var response = searchService.search(request);
        
        System.out.println("Response: " + objectMapper.writeValueAsString(response));
        
        assertNotNull(response);
        assertEquals(request.getId(), response.getId());
        assertEquals(request.getVer(), response.getVer());
        assertEquals(request.getParams().getMsgid().toString(), response.getParams().getMsgid());
        assertEquals("SUCCESS", response.getParams().getStatus());
        assertEquals("OK", response.getResponseCode());
        assertEquals(2, response.getResult().size());
        assertNull(response.getError());
        
        Map<String, Object> resultDoc1 = response.getResult().get(0);
        assertEquals("Third Wave Coffee", resultDoc1.get("name"));

        Map<String, Object> resultDoc2 = response.getResult().get(1);
        assertEquals("Cafe Coffee Day", resultDoc2.get("name"));
        
        List<String> coffeeTypes1 = (List<String>) resultDoc1.get("coffeeTypes");
        assertNotNull(coffeeTypes1);
        assertEquals(List.of("Espresso", "Americano", "Latte"), coffeeTypes1);

        List<String> coffeeTypes2 = (List<String>) resultDoc2.get("coffeeTypes");
        assertNotNull(coffeeTypes2);
        assertEquals(List.of("Filter Coffee", "Cappuccino"), coffeeTypes2);
        
        List<String> tags = (List<String>) resultDoc1.get("tags");
        assertNotNull(tags);
        assertEquals(List.of("coffee", "specialty", "roastery"), tags);
    }

    @Test
    void testAndFilterSearch() throws Exception {
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

        var response = searchService.search(request);

        System.out.println("Response: " + objectMapper.writeValueAsString(response));
        
        assertNotNull(response);
        assertEquals(request.getId(), response.getId());
        assertEquals(request.getVer(), response.getVer());
        assertEquals(request.getParams().getMsgid().toString(), response.getParams().getMsgid());
        assertEquals("SUCCESS", response.getParams().getStatus());
        assertEquals("OK", response.getResponseCode());
        assertEquals(1, response.getResult().size());
        assertNull(response.getError());
        
        Map<String, Object> resultDoc = response.getResult().get(0);
        assertEquals("Third Wave Coffee", resultDoc.get("name"));
        
        List<String> coffeeTypes = (List<String>) resultDoc.get("coffeeTypes");
        assertNotNull(coffeeTypes);
        assertTrue(coffeeTypes.contains("Americano"));
        
        List<String> tags = (List<String>) resultDoc.get("tags");
        assertNotNull(tags);
        assertTrue(tags.contains("coffee"));
    }

    @Test
    void testOrFilterSearch() throws Exception {
        var request = createSearchRequest();
        var filter = new SearchRequest.Filter();
        filter.setType("or");

        var field1 = new SearchRequest.Field();
        field1.setName("city");
        field1.setOp("eq");
        field1.setValue("chennai");

        var field2 = new SearchRequest.Field();
        field2.setName("coffeeTypes");
        field2.setOp("in");
        field2.setValue(List.of("latte"));

        filter.setFields(List.of(field1, field2));
        request.getRequest().getSearch().setFilters(List.of(filter));

        var response = searchService.search(request);

        System.out.println("Response: " + objectMapper.writeValueAsString(response));

        assertNotNull(response);
        assertEquals(request.getId(), response.getId());
        assertEquals(request.getVer(), response.getVer());
        assertEquals(request.getParams().getMsgid().toString(), response.getParams().getMsgid());
        assertEquals("SUCCESS", response.getParams().getStatus());
        assertEquals("OK", response.getResponseCode());
        assertEquals(2, response.getResult().size());
        assertNull(response.getError());

        Map<String, Object> resultDoc1 = response.getResult().get(0);
        assertEquals("Third Wave Coffee", resultDoc1.get("name"));

        Map<String, Object> resultDoc2 = response.getResult().get(1);
        assertEquals("Cafe Coffee Day", resultDoc2.get("name"));

        List<String> coffeeTypes = (List<String>) resultDoc1.get("coffeeTypes");
        assertNotNull(coffeeTypes);
        assertTrue(coffeeTypes.contains("Latte"));

        List<String> tags = (List<String>) resultDoc1.get("tags");
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