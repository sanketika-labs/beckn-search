package org.beckn.search.elasticsearch;

import co.elastic.clients.elasticsearch._types.query_dsl.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.beckn.search.model.SearchRequestDto;
import org.beckn.search.model.Location;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;

@Component
public class SearchQueryBuilder {
    private final ObjectMapper objectMapper;
    
    @Value("${search.geo.distance:1km}")
    private String geoDistance;

    public enum LogicalOperator {
        AND, OR
    }

    @Autowired
    public SearchQueryBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Query buildSearchQuery(SearchRequestDto request, LogicalOperator operator) {
        // Return match_all query if request is empty
        if (request.getMessage() == null) {
            return MatchAllQuery.of(m -> m)._toQuery();
        }

        BoolQuery.Builder mainQuery = new BoolQuery.Builder();
        List<Query> queries = new ArrayList<>();

        // Handle context location if present
        if (request.getContext() != null && request.getContext().getLocation() != null) {
            Map<String, Object> contextLocationFields = flattenFields("context_location", request.getContext().getLocation());
            System.out.println("Context Location Fields = " + contextLocationFields);
            for (Map.Entry<String, Object> entry : contextLocationFields.entrySet()) {
                String fieldName = entry.getKey();
                if (fieldName.endsWith("_gps")) {
                    String gpsValue = entry.getValue().toString();
                    String[] coordinates = gpsValue.split(",");
                    if (coordinates.length == 2) {
                        double lat = Double.parseDouble(coordinates[0]);
                        double lon = Double.parseDouble(coordinates[1]);
                        Query geoQuery = GeoDistanceQuery.of(g -> g
                                .field(fieldName)
                                .distance("1km")
                                .location(l -> l.text(lat + "," + lon)))._toQuery();
                        queries.add(geoQuery);
                    }
                }
            }
        }

        // Handle intent if present
        if (request.getMessage().getIntent() != null) {
            // Flatten the intent object
            Map<String, Object> flattenedFields = flattenFields("", request.getMessage().getIntent());
            
            // Group queries by field type for boosting
            List<Query> descriptorQueries = new ArrayList<>();
            List<Query> nonDescriptorQueries = new ArrayList<>();
            
            // Process each flattened field
            for (Map.Entry<String, Object> entry : flattenedFields.entrySet()) {
                String fieldName = entry.getKey();
                Object value = entry.getValue();
                
                // Handle GPS fields
                if (fieldName.toLowerCase().endsWith("_gps")) {
                    System.out.println("GPS Field = " + fieldName);
                    if (value != null) {
                        String gpsValue = value instanceof List ? ((List<?>) value).get(0).toString() : value.toString();
                        if (gpsValue.contains(",")) {
                            Query geoQuery = buildGeoDistanceQuery(gpsValue, fieldName);
                            if (geoQuery != null) {
                                queries.add(geoQuery);
                            }
                        }
                    }
                    continue;
                }
                
                // Handle fulfillment type
                if (fieldName.equals("providers_fulfillments_type") || fieldName.equals("provider_fulfillments_type")) {
                    if (value != null) {
                        String fulfillmentType = value instanceof List ? ((List<?>) value).get(0).toString() : value.toString();
                        Query matchQuery = MatchQuery.of(m -> m
                            .field("providers_fulfillments_type")
                            .query(fulfillmentType))
                            ._toQuery();
                        queries.add(matchQuery);
                    }
                    continue;
                }
                
                if (value != null) {
                    if (value instanceof List) {
                        List<?> values = (List<?>) value;
                        if (!values.isEmpty()) {
                            // For array fields, create a bool query with should clauses
                            BoolQuery.Builder arrayQuery = new BoolQuery.Builder();
                            for (Object val : values) {
                                if (val != null) {
                                    Query matchQuery = MatchQuery.of(m -> m
                                        .field(fieldName)
                                        .query(val.toString())
                                        .boost(fieldName.contains("descriptor") ? 2.0f : 1.0f))
                                        ._toQuery();
                                    arrayQuery.should(matchQuery);
                                }
                            }
                            // Add minimum_should_match parameter
                            arrayQuery.minimumShouldMatch("1");
                            
                            // Add to appropriate query list based on field type
                            if (fieldName.contains("descriptor")) {
                                descriptorQueries.add(arrayQuery.build()._toQuery());
                            } else {
                                nonDescriptorQueries.add(arrayQuery.build()._toQuery());
                            }
                        }
                    } else {
                        Query matchQuery = MatchQuery.of(m -> m
                            .field(fieldName)
                            .query(value.toString())
                            .boost(fieldName.contains("descriptor") ? 2.0f : 1.0f))
                            ._toQuery();
                        
                        if (fieldName.contains("descriptor")) {
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
        return flattenedFields;
    }

    private void flattenFieldsRecursive(String prefix, JsonNode jsonNode, Map<String, Object> flattenedFields) {
        if (jsonNode.isObject()) {
            jsonNode.fields().forEachRemaining(entry -> {
                String newPrefix = prefix.isEmpty() ? entry.getKey() : prefix + "_" + entry.getKey();
                
                // Special handling for provider/providers fields
                if (entry.getKey().equals("provider") || entry.getKey().equals("providers")) {
                    newPrefix = "providers";
                }
                
                flattenFieldsRecursive(newPrefix, entry.getValue(), flattenedFields);
            });
        } else if (jsonNode.isArray()) {
            List<Object> values = new ArrayList<>();
            Map<String, List<Object>> tempFields = new HashMap<>();

            jsonNode.elements().forEachRemaining(element -> {
                if (element.isObject()) {
                    Map<String, Object> elementFields = new HashMap<>();
                    flattenFieldsRecursive(prefix, element, elementFields);
                    
                    // For each field in the object, add its value to the corresponding list
                    elementFields.forEach((key, value) -> {
                        if (value instanceof List) {
                            // If the value is already a list, add all its elements
                            ((List<?>) value).forEach(v -> 
                                tempFields.computeIfAbsent(key, k -> new ArrayList<>()).add(v)
                            );
                        } else {
                            // Add single value to the list
                            tempFields.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
                        }
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
                    // For GPS fields, always take the first value
                    if (key.toLowerCase().endsWith("_gps")) {
                        flattenedFields.put(key, value.get(0));
                    } else {
                        flattenedFields.put(key, value);
                    }
                }
            });
        } else if (!jsonNode.isNull()) {
            flattenedFields.put(prefix, jsonNode.asText());
        }
    }

    private Query buildGeoDistanceQuery(String gps, String gpsField) {
        String[] coordinates = gps.split(",");
        if (coordinates.length != 2) {
            return null;
        }

        try {
            double lat = Double.parseDouble(coordinates[0].trim());
            double lon = Double.parseDouble(coordinates[1].trim());

            return GeoDistanceQuery.of(g -> g
                .field(gpsField)
                .distance(geoDistance)
                .location(l -> l.text(lat + "," + lon)))
                ._toQuery();

        } catch (NumberFormatException e) {
            System.err.println("Invalid GPS coordinates: " + e.getMessage());
        }

        return null;
    }
} 