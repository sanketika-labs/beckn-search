package org.beckn.service.impl;

import co.elastic.clients.elasticsearch._types.GeoLocation;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import lombok.RequiredArgsConstructor;
import org.beckn.model.SearchRequest;
import org.beckn.model.SearchResponse;
import org.beckn.service.SearchService;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;
import org.springframework.data.elasticsearch.core.query.StringQuery;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.geo.Point;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;

import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.Arrays;

import co.elastic.clients.json.JsonData;
import co.elastic.clients.elasticsearch._types.FieldValue;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {


    private static final Logger logger = LoggerFactory.getLogger(SearchServiceImpl.class);
    private final ElasticsearchOperations elasticsearchTemplate;

    @Value("${beck.fulltext.search.columns}")
    private List<String> fulltextSearchColumns;

    @Override
    public SearchResponse search(SearchRequest request) {
        try {
            List<co.elastic.clients.elasticsearch._types.query_dsl.Query> queries = new ArrayList<>();

            // Text search
            if (request.getRequest().getSearch().getText() != null && !request.getRequest().getSearch().getText().isEmpty()) {
                queries.add(createTextQuery(request.getRequest().getSearch().getText()));
            }

            // Geo spatial search
            if (request.getRequest().getSearch().getGeoSpatial() != null) {
                queries.add(createGeoQuery(request.getRequest().getSearch().getGeoSpatial()));
            }

            // Filters
            if (request.getRequest().getSearch().getFilters() != null) {
                co.elastic.clients.elasticsearch._types.query_dsl.Query filterQuery = createFilterQuery(request.getRequest().getSearch().getFilters());
                if (filterQuery != null) {
                    queries.add(filterQuery);
                }
            }

            co.elastic.clients.elasticsearch._types.query_dsl.Query finalQuery;
            if (queries.isEmpty()) {
                finalQuery = new co.elastic.clients.elasticsearch._types.query_dsl.Query.Builder()
                        .matchAll(new MatchAllQuery.Builder().build())
                        .build();
            } else {
                finalQuery = new co.elastic.clients.elasticsearch._types.query_dsl.Query.Builder()
                        .bool(new BoolQuery.Builder()
                                .must(queries)
                                .build())
                        .build();
            }

            // Create native search query
            Query searchQuery = new NativeQueryBuilder()
                    .withQuery(finalQuery)
                    .withPageable(org.springframework.data.domain.PageRequest.of(
                            request.getRequest().getSearch().getPage().getFrom(),
                            request.getRequest().getSearch().getPage().getSize()))
                    .build();

            logger.debug("Generated query: {}", finalQuery.toString());

            SearchHits<?> hits = elasticsearchTemplate.search(searchQuery, Map.class,
                    org.springframework.data.elasticsearch.core.mapping.IndexCoordinates.of(
                            request.getRequest().getContext().getDomain()
                    )
            );

            List<Map<String, Object>> results = hits.getSearchHits().stream()
                    .map(hit -> (Map<String, Object>) hit.getContent())
                    .collect(Collectors.toList());

            return SearchResponse.builder()
                    .id(request.getId())
                    .ver(request.getVer())
                    .ts(OffsetDateTime.now())
                    .params(SearchResponse.Params.builder()
                            .status("SUCCESS")
                            .msgid(request.getParams().getMsgid().toString())
                            .build())
                    .responseCode("OK")
                    .result(results)
                    .build();

        } catch (Exception e) {
            e.printStackTrace();
            return SearchResponse.builder()
                    .id(request.getId())
                    .ver(request.getVer())
                    .ts(OffsetDateTime.now())
                    .params(SearchResponse.Params.builder()
                            .status("ERROR")
                            .msgid(request.getParams().getMsgid().toString())
                            .build())
                    .responseCode("INTERNAL_SERVER_ERROR")
                    .result(List.of())
                    .error(SearchResponse.Error.builder()
                            .code("SEARCH_ERROR")
                            .message(e.getMessage())
                            .build())
                    .build();
        }
    }

    private co.elastic.clients.elasticsearch._types.query_dsl.Query createTextQuery(String text) {
        return new co.elastic.clients.elasticsearch._types.query_dsl.Query.Builder()
                .multiMatch(new MultiMatchQuery.Builder()
                        .query(text)
                        .fields(fulltextSearchColumns)
                        .type(TextQueryType.BestFields)
                        .build())
                .build();
    }

    private co.elastic.clients.elasticsearch._types.query_dsl.Query createGeoQuery(SearchRequest.GeoSpatial geoSpatial) {
        return new co.elastic.clients.elasticsearch._types.query_dsl.Query.Builder()
                .geoDistance(new GeoDistanceQuery.Builder()
                        .field("location")
                        .distance(geoSpatial.getDistance() + geoSpatial.getUnit())
                        .location(new GeoLocation.Builder()
                                .latlon(new co.elastic.clients.elasticsearch._types.LatLonGeoLocation.Builder()
                                        .lat(geoSpatial.getLocation().getLat())
                                        .lon(geoSpatial.getLocation().getLon())
                                        .build())
                                .build())
                        .build())
                .build();
    }

    private co.elastic.clients.elasticsearch._types.query_dsl.Query createFilterQuery(List<SearchRequest.Filter> filters) {
        if (filters.size() == 1) {
            SearchRequest.Filter filter = filters.get(0);
            List<co.elastic.clients.elasticsearch._types.query_dsl.Query> fieldQueries = new ArrayList<>();

            for (SearchRequest.Field field : filter.getFields()) {
                if (field.getType() != null && ("or".equals(field.getType()) || "and".equals(field.getType()))) {
                    // Handle nested filter conditions
                    fieldQueries.add(createFilterQuery(List.of(new SearchRequest.Filter() {{
                        setType(field.getType());
                        setFields(field.getFields());
                    }})));
                } else {
                    // Handle simple field conditions
                    fieldQueries.add(createFieldQuery(field));
                }
            }

            if ("or".equals(filter.getType())) {
                return new co.elastic.clients.elasticsearch._types.query_dsl.Query.Builder()
                        .bool(new BoolQuery.Builder()
                                .should(fieldQueries)
                                .minimumShouldMatch("1")
                                .build())
                        .build();
            } else {
                // For "and" type or no type specified
                return new co.elastic.clients.elasticsearch._types.query_dsl.Query.Builder()
                        .bool(new BoolQuery.Builder()
                                .must(fieldQueries)
                                .build())
                        .build();
            }
        } else {
            // Handle multiple filters
            List<co.elastic.clients.elasticsearch._types.query_dsl.Query> filterQueries = new ArrayList<>();
            for (SearchRequest.Filter filter : filters) {
                filterQueries.add(createFilterQuery(List.of(filter)));
            }
            return new co.elastic.clients.elasticsearch._types.query_dsl.Query.Builder()
                    .bool(new BoolQuery.Builder()
                            .must(filterQueries)
                            .build())
                    .build();
        }
    }

    private co.elastic.clients.elasticsearch._types.query_dsl.Query createFieldQuery(SearchRequest.Field field) {
        if (field.getType() != null && ("or".equals(field.getType()) || "and".equals(field.getType()))) {
            return createFilterQuery(List.of(new SearchRequest.Filter() {{
                setType(field.getType());
                setFields(field.getFields());
            }}));
        }

        String op = field.getOp();
        String name = field.getName();
        Object value = field.getValue();

        switch (op) {
            case "eq":
                /*
                return new co.elastic.clients.elasticsearch._types.query_dsl.Query.Builder()
                        .term(new TermQuery.Builder()
                                .field(name)
                                .value(FieldValue.of(value.toString()))
                                .build())
                        .build();
                 */
                return new co.elastic.clients.elasticsearch._types.query_dsl.Query.Builder()
                        .match(new MatchQuery.Builder()
                                .field(name)
                                .query(FieldValue.of(value.toString()))
                                .build())
                        .build();
            case "in":
                List<FieldValue> values = ((List<?>) value).stream()
                        .map(v -> FieldValue.of(v.toString())).toList();
                // if (name.equals("coffeeTypes") || name.equals("tags")) {
                // For array fields, use match query to check if array contains the value
                return new co.elastic.clients.elasticsearch._types.query_dsl.Query.Builder()
                        .match(new MatchQuery.Builder()
                                .field(name)
                                .query(values.get(0).stringValue())
                                .build())
                        .build();
            // }
                /*
                else {
                    return new co.elastic.clients.elasticsearch._types.query_dsl.Query.Builder()
                            .terms(new TermsQuery.Builder()
                                    .field(name)
                                    .terms(new TermsQueryField.Builder()
                                            .value(values)
                                            .build())
                                    .build())
                            .build();
                }
                */
            case "lt":
                return new co.elastic.clients.elasticsearch._types.query_dsl.Query.Builder()
                        .range(new RangeQuery.Builder()
                                .field(name)
                                .lt(JsonData.of(value))
                                .build())
                        .build();
            case "gt":
                return new co.elastic.clients.elasticsearch._types.query_dsl.Query.Builder()
                        .range(new RangeQuery.Builder()
                                .field(name)
                                .gt(JsonData.of(value))
                                .build())
                        .build();
            default:
                throw new IllegalArgumentException("Unsupported operator: " + op);
        }
    }
}