package org.beckn.search.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpHost;
import org.beckn.search.transformer.SearchResponseTransformer;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.convert.MappingElasticsearchConverter;
import org.springframework.data.elasticsearch.core.mapping.SimpleElasticsearchMappingContext;

@TestConfiguration
public class ElasticsearchTestConfig {

    @Value("${elasticsearch.host:localhost}")
    private String host;

    @Value("${elasticsearch.port:9200}")
    private int port;

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public RestClient restClient() {
        return RestClient.builder(new HttpHost(host, port))
                .setRequestConfigCallback(requestConfigBuilder -> requestConfigBuilder
                        .setConnectTimeout(5000)
                        .setSocketTimeout(60000))
                .build();
    }

    @Bean
    public ElasticsearchClient elasticsearchClient(RestClient restClient, ObjectMapper objectMapper) {
        return new ElasticsearchClient(
                new RestClientTransport(
                        restClient,
                        new JacksonJsonpMapper(objectMapper)
                )
        );
    }

    @Bean
    public SearchQueryBuilder searchQueryBuilder(ObjectMapper objectMapper) {
        return new SearchQueryBuilder(objectMapper);
    }

    @Bean
    public SearchResponseTransformer searchResponseTransformer(ObjectMapper objectMapper) {
        return new SearchResponseTransformer(objectMapper);
    }

    @Bean
    public SearchService searchService(
            ElasticsearchClient elasticsearchClient,
            SearchQueryBuilder searchQueryBuilder,
            ObjectMapper objectMapper,
            SearchResponseTransformer searchResponseTransformer) {
        return new SearchService(elasticsearchClient, searchQueryBuilder, objectMapper, searchResponseTransformer);
    }
} 