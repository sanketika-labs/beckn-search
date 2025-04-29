package org.beckn.search.elasticsearch;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch._types.FieldValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.beckn.search.model.SearchRequestDto;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class SearchQueryBuilder {
    private final ObjectMapper objectMapper;

    public SearchQueryBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Query buildSearchQuery(SearchRequestDto request) {
        List<Query> shouldQueries = new ArrayList<>();
        
        // Handle null request or missing intent
        if (request == null || request.getMessage() == null || request.getMessage().getIntent() == null) {
            return QueryBuilders.bool()
                .should(shouldQueries)
                .build()._toQuery();
        }
        
        // Get the intent node from the request
        JsonNode intentNode = objectMapper.valueToTree(request.getMessage().getIntent());
        Map<String, Object> flattenedFields = new HashMap<>();
        
        // Flatten the intent fields
        flattenFields("", intentNode, flattenedFields);
        
        // Build queries from flattened fields
        flattenedFields.forEach((fieldPath, value) -> {
            if (value != null) {
                if (value instanceof List<?>) {
                    // Handle array values with terms query
                    List<?> values = (List<?>) value;
                    if (!values.isEmpty()) {
                        List<FieldValue> fieldValues = values.stream()
                            .map(v -> FieldValue.of(v.toString()))
                            .collect(Collectors.toList());
                            
                        shouldQueries.add(QueryBuilders.terms()
                            .field(fieldPath)
                            .terms(builder -> builder.value(fieldValues))
                            .build()._toQuery());
                    }
                } else {
                    // Handle single value with match query
                    shouldQueries.add(QueryBuilders.match()
                        .field(fieldPath)
                        .query(value.toString())
                        .build()._toQuery());
                }
            }
        });
        
        return QueryBuilders.bool()
            .should(shouldQueries)
            .build()._toQuery();
    }

    private void flattenFields(String prefix, JsonNode node, Map<String, Object> flattenedFields) {
        if (node == null) {
            return;
        }
        
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String newPrefix = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
                flattenFields(newPrefix, entry.getValue(), flattenedFields);
            });
        } else if (node.isArray()) {
            List<Object> values = new ArrayList<>();
            node.elements().forEachRemaining(element -> {
                if (element.isObject()) {
                    // For objects in arrays, flatten their fields individually
                    flattenFields(prefix, element, flattenedFields);
                } else {
                    // For primitive values, collect them for terms query
                    values.add(element.asText());
                }
            });
            if (!values.isEmpty()) {
                flattenedFields.put(prefix, values);
            }
        } else if (!node.isNull()) {
            flattenedFields.put(prefix, node.asText());
        }
    }
} 