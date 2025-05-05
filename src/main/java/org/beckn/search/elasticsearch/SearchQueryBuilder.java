package org.beckn.search.elasticsearch;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchAllQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch._types.FieldValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.beckn.search.model.Intent;
import org.beckn.search.model.Location;
import org.beckn.search.model.SearchRequestDto;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class SearchQueryBuilder {
    private final ObjectMapper objectMapper;
    
    public enum LogicalOperator {
        AND, OR
    }

    public SearchQueryBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Query buildSearchQuery(SearchRequestDto request, LogicalOperator operator) {
        // Return match_all query if request is empty
        if (request.getMessage() == null || request.getMessage().getIntent() == null) {
            return MatchAllQuery.of(m -> m)._toQuery();
        }

        BoolQuery.Builder mainQuery = new BoolQuery.Builder();
        List<Query> queries = new ArrayList<>();

        // Add intent filters
        Map<String, Object> flattenedFields = flattenFields("", request.getMessage().getIntent());
        System.out.println("JSONNode = ");
        System.out.println("Flattened Field Size = " + flattenedFields.size());
            
        // Group queries by field type for boosting
        List<Query> descriptorQueries = new ArrayList<>();
        List<Query> nonDescriptorQueries = new ArrayList<>();
            
        for (Map.Entry<String, Object> entry : flattenedFields.entrySet()) {
            System.out.println("Key = " + entry.getKey() + " Value = " + entry.getValue());
            if (entry.getValue() != null) {
                if (entry.getValue() instanceof List) {
                    List<?> values = (List<?>) entry.getValue();
                    if (!values.isEmpty()) {
                        // For array fields, create a bool query with should clauses
                        BoolQuery.Builder arrayQuery = new BoolQuery.Builder();
                        for (Object value : values) {
                            if (value != null) {
                                Query matchQuery = MatchQuery.of(m -> m
                                    .field(entry.getKey())
                                    .query(value.toString())
                                    .boost(2.0f))
                                    ._toQuery();
                                arrayQuery.should(matchQuery);
                            }
                        }
                        // Add minimum_should_match parameter
                        arrayQuery.minimumShouldMatch("1");
                            
                        // Add boost based on field type
                        if (entry.getKey().contains("descriptor")) {
                            descriptorQueries.add(arrayQuery.build()._toQuery());
                        } else {
                            nonDescriptorQueries.add(arrayQuery.build()._toQuery());
                        }
                    }
                } else {
                    Query matchQuery = MatchQuery.of(m -> m
                        .field(entry.getKey())
                        .query(entry.getValue().toString())
                        .boost(entry.getKey().contains("descriptor") ? 2.0f : 1.0f))
                        ._toQuery();
                            
                    if (entry.getKey().contains("descriptor")) {
                        descriptorQueries.add(matchQuery);
                    } else {
                        nonDescriptorQueries.add(matchQuery);
                    }
                }
            }
        }
            
        // Combine descriptor and non-descriptor queries with different boosts
        if (!descriptorQueries.isEmpty()) {
            BoolQuery.Builder descriptorBool = new BoolQuery.Builder();
            if (operator == LogicalOperator.AND) {
                descriptorBool.must(descriptorQueries);
            } else {
                descriptorBool.should(descriptorQueries)
                    .minimumShouldMatch("1");
            }
            queries.add(descriptorBool.boost(2.0f).build()._toQuery());
        }
            
        if (!nonDescriptorQueries.isEmpty()) {
            BoolQuery.Builder nonDescriptorBool = new BoolQuery.Builder();
            if (operator == LogicalOperator.AND) {
                nonDescriptorBool.must(nonDescriptorQueries);
            } else {
                nonDescriptorBool.should(nonDescriptorQueries)
                    .minimumShouldMatch("1");
            }
            queries.add(nonDescriptorBool.build()._toQuery());
        }

        // Add location filters if present
        if (request.getContext() != null && request.getContext().getLocation() != null) {
            addLocationFilters(request.getContext().getLocation(), queries);
        }

        // Combine all queries based on operator
        if (!queries.isEmpty()) {
            if (operator == LogicalOperator.AND) {
                mainQuery.must(queries);
            } else {
                mainQuery.should(queries)
                    .minimumShouldMatch("1");
            }
            return mainQuery.build()._toQuery();
        }

        // Return match_all query if no filters
        return MatchAllQuery.of(m -> m)._toQuery();
    }

    public Map<String, Object> flattenFields(String prefix, Object object) {
        Map<String, Object> flattenedFields = new HashMap<>();
        JsonNode jsonNode = objectMapper.valueToTree(object);
        flattenFieldsRecursive(prefix, jsonNode, flattenedFields);
        System.out.println("JSONNode = " + jsonNode.asText());
        return flattenedFields;
    }

    private void flattenFieldsRecursive(String prefix, JsonNode jsonNode, Map<String, Object> flattenedFields) {
        if (jsonNode.isObject()) {
            jsonNode.fields().forEachRemaining(entry -> {
                String newPrefix = prefix.isEmpty() ? entry.getKey() : prefix + "_" + entry.getKey();
                flattenFieldsRecursive(newPrefix, entry.getValue(), flattenedFields);
            });
        } else if (jsonNode.isArray()) {
            List<Object> values = new ArrayList<>();
            Map<String, List<Object>> tempFields = new HashMap<>();

            jsonNode.elements().forEachRemaining(element -> {
                if (element.isObject()) {
                    Map<String, Object> elementFields = new HashMap<>();
                    flattenFieldsRecursive(prefix, element, elementFields);
                    
                    // Merge fields into tempFields
                    elementFields.forEach((key, value) -> {
                        tempFields.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
                    });
                } else if (!element.isNull()) {
                    values.add(element.asText());
                }
            });

            // Add collected simple values if any
            if (!values.isEmpty()) {
                flattenedFields.put(prefix, values);
            }

            // Add collected object fields
            tempFields.forEach((key, value) -> {
                if (!value.isEmpty()) {
                    flattenedFields.put(key, value);
                }
            });
        } else if (!jsonNode.isNull()) {
            flattenedFields.put(prefix, jsonNode.asText());
        }
    }

    private void addLocationFilters(Location location, List<Query> queries) {
        // Add country filters
        if (location.getCountry() != null) {
            if (location.getCountry().getName() != null) {
                queries.add(MatchQuery.of(m -> m
                    .field("location_country_name")
                    .query(location.getCountry().getName()))
                    ._toQuery());
            }
            if (location.getCountry().getCode() != null) {
                queries.add(MatchQuery.of(m -> m
                    .field("location_country_code")
                    .query(location.getCountry().getCode()))
                    ._toQuery());
            }
        }
        
        // Add city filters
        if (location.getCity() != null) {
            if (location.getCity().getName() != null) {
                queries.add(MatchQuery.of(m -> m
                    .field("location_city_name")
                    .query(location.getCity().getName()))
                    ._toQuery());
            }
            if (location.getCity().getCode() != null) {
                queries.add(MatchQuery.of(m -> m
                    .field("location_city_code")
                    .query(location.getCity().getCode()))
                    ._toQuery());
            }
        }
    }
} 