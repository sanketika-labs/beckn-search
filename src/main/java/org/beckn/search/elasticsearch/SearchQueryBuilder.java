package org.beckn.search.elasticsearch;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch._types.FieldValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.beckn.search.model.SearchRequestDto;
import org.beckn.search.model.Location;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class SearchQueryBuilder {
    private final ObjectMapper objectMapper;
    
    public enum LogicalOperator {
        AND,
        OR
    }

    public SearchQueryBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Query buildSearchQuery(SearchRequestDto request, LogicalOperator operator) {
        BoolQuery.Builder mainQuery = new BoolQuery.Builder();
        List<Query> queries = new ArrayList<>();

        // Add intent filters
        if (request.getMessage() != null && request.getMessage().getIntent() != null) {
            Map<String, Object> flattenedFields = flattenFields("", request.getMessage().getIntent());
            for (Map.Entry<String, Object> entry : flattenedFields.entrySet()) {
                if (entry.getValue() != null) {
                    Query query = QueryBuilders.matchPhrase()
                        .field(entry.getKey())
                        .query(entry.getValue().toString())
                        .build()
                        ._toQuery();
                    queries.add(query);
                }
            }
        }

        if (operator == LogicalOperator.AND) {
            mainQuery.must(queries);
        } else {
            mainQuery.should(queries);
        }

        return mainQuery.build()._toQuery();
    }

    public Map<String, Object> flattenFields(String prefix, Object object) {
        Map<String, Object> flattenedFields = new HashMap<>();
        JsonNode jsonNode = objectMapper.valueToTree(object);
        flattenFieldsRecursive(prefix, jsonNode, flattenedFields);
        return flattenedFields;
    }

    private void flattenFieldsRecursive(String prefix, JsonNode jsonNode, Map<String, Object> flattenedFields) {
        if (jsonNode.isObject()) {
            jsonNode.fields().forEachRemaining(entry -> {
                String newPrefix = prefix.isEmpty() ? entry.getKey() : prefix + "_" + entry.getKey();
                flattenFieldsRecursive(newPrefix, entry.getValue(), flattenedFields);
            });
        } else if (jsonNode.isArray()) {
            List<String> values = new ArrayList<>();
            jsonNode.elements().forEachRemaining(element -> {
                if (element.isTextual()) {
                    values.add(element.asText());
                }
            });
            if (!values.isEmpty()) {
                flattenedFields.put(prefix, values);
            }
        } else if (!jsonNode.isNull()) {
            flattenedFields.put(prefix, jsonNode.asText());
        }
    }
} 