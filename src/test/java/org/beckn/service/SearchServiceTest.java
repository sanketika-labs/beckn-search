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
        elasticsearchTemplate.indexOps(SearchDocument.class).create();
        elasticsearchTemplate.indexOps(SearchDocument.class).putMapping();
        
        // Add sample document
        var document1 = new SearchDocument();
        document1.setId(UUID.randomUUID().toString());
        document1.setName("Third Wave Coffee");
        document1.setDescription("Premium Organic Brews");
        document1.setTags(List.of("coffee", "cafe"));
        document1.setCity("Bangalore");
        document1.setState("Karnataka");
        document1.setCoffeeTypes(List.of("Americano", "Espresso", "Sea Salt Mocha"));
        document1.setLocation(new org.springframework.data.elasticsearch.core.geo.GeoPoint(40.758896, -73.985130));
        elasticsearchTemplate.save(document1);

        // Add sample document
        var document2 = new SearchDocument();
        document2.setId(UUID.randomUUID().toString());
        document2.setName("Starbucks");
        document2.setDescription("one person, one cup and one neighborhood at a time");
        document2.setTags(List.of("coffee", "cafe"));
        document2.setCity("Bangalore");
        document2.setState("Karnataka");
        document2.setCoffeeTypes(List.of("Cappucino", "Chai Tea Latte", "Cold Coffee"));
        document2.setLocation(new org.springframework.data.elasticsearch.core.geo.GeoPoint(40.768896, -73.995130));
        elasticsearchTemplate.save(document2);

        // Add sample document
        var document3 = new SearchDocument();
        document3.setId(UUID.randomUUID().toString());
        document3.setName("Cafe Coffee Day");
        document3.setDescription("Best coffee in town");
        document3.setTags(List.of("coffee", "cafe"));
        document3.setCity("Chennai");
        document3.setState("Tamil Nadu");
        document3.setCoffeeTypes(List.of("Lemon Green Coffee", "Honey Cinnamon Coffee", "Vanilla Latte"));
        document3.setLocation(new org.springframework.data.elasticsearch.core.geo.GeoPoint(40.748896, -73.975130));
        elasticsearchTemplate.save(document3);
        elasticsearchTemplate.indexOps(IndexCoordinates.of("retail")).refresh();
    }

    @Test
    void testTextSearch() {
        var request = createSearchRequest();
        request.getRequest().getSearch().setText("Latte");
        
        var result = searchService.search(request);
        
        assertNotNull(result);
        System.out.println("Test result...");
        System.out.println(result.getSearchHits().get(0));
        System.out.println(result.getSearchHits().get(1));
        assertEquals(2, result.getTotalHits());
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
        System.out.println("Test result...");
        System.out.println(result.getSearchHits().get(0));
        assertEquals(1, result.getTotalHits());
    }

    @Test
    void testFilterSearch() {
        var request = createSearchRequest();
        var filter = new SearchRequest.Filter();
        filter.setType("and");
        
        // First field group (OR condition)
        var field1 = new SearchRequest.Field();
        field1.setType("or");
        var field1_1 = new SearchRequest.Field();
        field1_1.setName("tags");
        field1_1.setOp("in");
        field1_1.setValue(List.of("coffee"));
        var field1_2 = new SearchRequest.Field();
        field1_2.setType("and");
        var field1_2_1 = new SearchRequest.Field();
        field1_2_1.setName("name");
        field1_2_1.setOp("eq");
        field1_2_1.setValue("Third Wave Coffee");
        var field1_2_2 = new SearchRequest.Field();
        field1_2_2.setName("coffeeTypes");
        field1_2_2.setOp("in");
        field1_2_2.setValue(List.of("Americano"));
        field1_2.setFields(List.of(field1_2_1, field1_2_2));
        field1.setFields(List.of(field1_1, field1_2));
        
        // Second field group (AND condition)
        var field2 = new SearchRequest.Field();
        field2.setType("and");
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
        System.out.println("Test result...");
        System.out.println(result.getSearchHits().get(0));
        assertEquals(1, result.getTotalHits());
    }

    @Test
    void testFilterSearch1() {
        var request = createSearchRequest();
        var filter = new SearchRequest.Filter();
        filter.setType("and");

        // Create simple field conditions without nesting
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
        System.out.println("Test result...");
        System.out.println("Total hits: " + result.getTotalHits());
        if (result.getTotalHits() > 0) {
            System.out.println(result.getSearchHits().get(0));
        } else {
            System.out.println("No results found");
        }
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