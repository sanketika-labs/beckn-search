package org.beckn.service;

import org.beckn.model.SearchRequest;
import org.beckn.model.SearchDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(properties = {
    "elasticsearch.index.name=retail"
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
    private SearchService searchService;

    @Autowired
    private ElasticsearchTemplate elasticsearchTemplate;

    @BeforeEach
    void setup() {
        // Delete index if exists
        if (elasticsearchTemplate.indexOps(IndexCoordinates.of("retail")).exists()) {
            elasticsearchTemplate.indexOps(IndexCoordinates.of("retail")).delete();
        }
        
        // Create index with mapping
        elasticsearchTemplate.indexOps(IndexCoordinates.of("retail")).create();
        
        // Add sample document
        var document = new SearchDocument();
        document.setId(UUID.randomUUID().toString());
        document.setName("Coffee Shop");
        document.setDescription("Best coffee in town");
        document.setTags(List.of("coffee", "cafe"));
        document.setLocation(new org.springframework.data.elasticsearch.core.geo.GeoPoint(40.758896, -73.985130));
        
        elasticsearchTemplate.save(document);
        elasticsearchTemplate.indexOps(IndexCoordinates.of("retail")).refresh();
    }

    @Test
    void testTextSearch() {
        var request = createSearchRequest();
        request.getRequest().getSearch().setText("coffee");
        
        var result = searchService.search(request);
        
        assertNotNull(result);
        System.out.println("Test result...");
        System.out.println(result.getSearchHits().get(0));
        assertEquals(1, result.getTotalHits());
    }

    @Test
    void testGeoSearch() {
        var request = createSearchRequest();
        var geo = new SearchRequest.GeoSpatial();
        geo.setDistance("1");
        geo.setUnit("km");
        var location = new SearchRequest.Location();
        location.setLat(40.758896);
        location.setLon(-73.985130);
        geo.setLocation(location);
        request.getRequest().getSearch().setGeoSpatial(geo);
        
        var result = searchService.search(request);
        
        assertNotNull(result);
        assertEquals(1, result.getTotalHits());
    }

    @Test
    void testFilterSearch() {
        var request = createSearchRequest();
        var filter = new SearchRequest.Filter();
        filter.setType("and");
        var field = new SearchRequest.Field();
        field.setName("tags");
        field.setOp("in");
        field.setValue(List.of("coffee"));
        filter.setFields(List.of(field));
        request.getRequest().getSearch().setFilters(List.of(filter));
        
        var result = searchService.search(request);
        
        assertNotNull(result);
        assertEquals(1, result.getTotalHits());
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