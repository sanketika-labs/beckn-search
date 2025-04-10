package org.beckn.service.impl;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.GeoLocation;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.json.JsonData;
import lombok.RequiredArgsConstructor;
import org.beckn.model.SearchDocument;
import org.beckn.model.SearchRequest;
import org.beckn.service.SearchService;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.SearchHitsImpl;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final ElasticsearchTemplate elasticsearchTemplate;

    @Override
    public SearchHits<SearchDocument> search(SearchRequest request) {
        List<co.elastic.clients.elasticsearch._types.query_dsl.Query> queries = new ArrayList<>();
        
        // Text search
        if (request.getRequest().getSearch().getText() != null) {
            queries.add(createTextQuery(request.getRequest().getSearch().getText()));
        }
        
        // Geo spatial search
        if (request.getRequest().getSearch().getGeoSpatial() != null) {
            queries.add(createGeoQuery(request.getRequest().getSearch().getGeoSpatial()));
        }
        
        // Filters
        if (request.getRequest().getSearch().getFilters() != null) {
            queries.add(createFilterQuery(request.getRequest().getSearch().getFilters()));
        }
        
        co.elastic.clients.elasticsearch._types.query_dsl.Query boolQuery = new co.elastic.clients.elasticsearch._types.query_dsl.Query.Builder()
                .bool(new BoolQuery.Builder()
                        .must(queries)
                        .build())
                .build();

        Criteria criteria = new Criteria();
        Query searchQuery = new CriteriaQuery(criteria);
        searchQuery.setPageable(org.springframework.data.domain.PageRequest.of(
                request.getRequest().getSearch().getPage().getFrom(),
                request.getRequest().getSearch().getPage().getSize()));

        return elasticsearchTemplate.search(searchQuery, SearchDocument.class);
    }

    private co.elastic.clients.elasticsearch._types.query_dsl.Query createTextQuery(String text) {
        return new co.elastic.clients.elasticsearch._types.query_dsl.Query.Builder()
                .multiMatch(new MultiMatchQuery.Builder()
                        .query(text)
                        .fields("name", "description")
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
        List<co.elastic.clients.elasticsearch._types.query_dsl.Query> filterQueries = new ArrayList<>();
        
        for (SearchRequest.Filter filter : filters) {
            if ("or".equals(filter.getType())) {
                List<co.elastic.clients.elasticsearch._types.query_dsl.Query> orQueries = new ArrayList<>();
                for (SearchRequest.Field field : filter.getFields()) {
                    orQueries.add(createFieldQuery(field));
                }
                filterQueries.add(new co.elastic.clients.elasticsearch._types.query_dsl.Query.Builder()
                        .bool(new BoolQuery.Builder()
                                .should(orQueries)
                                .minimumShouldMatch("1")
                                .build())
                        .build());
            } else if ("and".equals(filter.getType())) {
                List<co.elastic.clients.elasticsearch._types.query_dsl.Query> andQueries = new ArrayList<>();
                for (SearchRequest.Field field : filter.getFields()) {
                    andQueries.add(createFieldQuery(field));
                }
                filterQueries.add(new co.elastic.clients.elasticsearch._types.query_dsl.Query.Builder()
                        .bool(new BoolQuery.Builder()
                                .must(andQueries)
                                .build())
                        .build());
            }
        }
        
        return new co.elastic.clients.elasticsearch._types.query_dsl.Query.Builder()
                .bool(new BoolQuery.Builder()
                        .must(filterQueries)
                        .build())
                .build();
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
                return new co.elastic.clients.elasticsearch._types.query_dsl.Query.Builder()
                        .term(new TermQuery.Builder()
                                .field(name)
                                .value(FieldValue.of(value.toString()))
                                .build())
                        .build();
            case "in":
                List<FieldValue> values = ((List<?>) value).stream()
                        .map(v -> FieldValue.of(v.toString()))
                        .collect(Collectors.toList());
                return new co.elastic.clients.elasticsearch._types.query_dsl.Query.Builder()
                        .terms(new TermsQuery.Builder()
                                .field(name)
                                .terms(new TermsQueryField.Builder()
                                        .value(values)
                                        .build())
                                .build())
                        .build();
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