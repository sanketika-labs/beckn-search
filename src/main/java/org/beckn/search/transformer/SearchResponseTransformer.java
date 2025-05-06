package org.beckn.search.transformer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.beckn.search.model.SearchResponseDto;
import org.beckn.search.model.Context;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class SearchResponseTransformer {
    private final ObjectMapper objectMapper;

    public String extractRawCatalog(String catalogJson) throws IOException {
        JsonNode catalogNode = objectMapper.readTree(catalogJson);
        if (catalogNode.has("raw_catalog")) {
            return catalogNode.get("raw_catalog").asText();
        }
        return catalogJson;
    }

    public SearchResponseDto transformToResponse(String rawCatalog) {
        if (rawCatalog == null || rawCatalog.trim().isEmpty()) {
            SearchResponseDto errorResponse = new SearchResponseDto();
            SearchResponseDto.Error error = new SearchResponseDto.Error();
            error.setCode("NO_CATALOG_DATA");
            error.setMessage("Raw catalog cannot be null or empty");
            errorResponse.setError(error);
            return errorResponse;
        }

        try {
            JsonNode catalogNode = objectMapper.readTree(rawCatalog);
            
            // If we have an array of raw catalogs
            if (catalogNode.isArray() && catalogNode.size() > 0) {
                // Create a new response
                SearchResponseDto response = new SearchResponseDto();
                SearchResponseDto.Message message = new SearchResponseDto.Message();
                SearchResponseDto.Catalog catalog = new SearchResponseDto.Catalog();
                
                // Get the first catalog's descriptor if available
                JsonNode firstCatalog = catalogNode.get(0);
                if (firstCatalog != null) {
                    SearchResponseDto firstResponse = objectMapper.readValue(firstCatalog.asText(), SearchResponseDto.class);
                    if (firstResponse.getMessage() != null && 
                        firstResponse.getMessage().getCatalog() != null && 
                        firstResponse.getMessage().getCatalog().getDescriptor() != null) {
                        catalog.setDescriptor(firstResponse.getMessage().getCatalog().getDescriptor());
                    }
                }
                
                // Create providers array
                ArrayNode providers = objectMapper.createArrayNode();
                
                // Process each raw catalog
                for (JsonNode rawCatalogNode : catalogNode) {
                    // Parse each raw catalog
                    SearchResponseDto catalogResponse = objectMapper.readValue(rawCatalogNode.asText(), SearchResponseDto.class);
                    
                    // Get providers from this catalog and add them to the array
                    JsonNode catalogProviders = catalogResponse.getMessage().getCatalog().getProviders();
                    if (catalogProviders != null) {
                        if (catalogProviders.isArray()) {
                            // Process each provider
                            catalogProviders.forEach(provider -> {
                                // Ensure items is an array
                                JsonNode items = provider.get("items");
                                if (items != null && !items.isArray()) {
                                    // If items is a single object, convert it to an array
                                    ObjectNode providerObj = (ObjectNode) provider;
                                    ArrayNode itemsArray = objectMapper.createArrayNode();
                                    itemsArray.add(items);
                                    providerObj.set("items", itemsArray);
                                }
                                providers.add(provider);
                            });
                        } else {
                            // Single provider object
                            ObjectNode provider = (ObjectNode) catalogProviders;
                            // Ensure items is an array
                            JsonNode items = provider.get("items");
                            if (items != null && !items.isArray()) {
                                ArrayNode itemsArray = objectMapper.createArrayNode();
                                itemsArray.add(items);
                                provider.set("items", itemsArray);
                            }
                            providers.add(provider);
                        }
                    }
                }
                
                // Set the providers array
                catalog.setProviders(providers);
                message.setCatalog(catalog);
                response.setMessage(message);
                
                // Add error if no providers found
                if (providers.size() == 0) {
                    SearchResponseDto.Error error = new SearchResponseDto.Error();
                    error.setCode("NO_PROVIDERS_FOUND");
                    error.setMessage("No matching providers found in the search results");
                    response.setError(error);
                }
                
                return response;
            } else if (catalogNode.isArray()) {
                // Empty array
                SearchResponseDto errorResponse = new SearchResponseDto();
                SearchResponseDto.Error error = new SearchResponseDto.Error();
                error.setCode("NO_SEARCH_RESULTS");
                error.setMessage("No results found for the search criteria");
                errorResponse.setError(error);
                return errorResponse;
            } else {
                // Single raw catalog
                SearchResponseDto response = objectMapper.readValue(rawCatalog, SearchResponseDto.class);
                
                // Ensure providers exist and items are arrays
                if (response.getMessage() != null && 
                    response.getMessage().getCatalog() != null && 
                    response.getMessage().getCatalog().getProviders() != null) {
                    
                    JsonNode providers = response.getMessage().getCatalog().getProviders();
                    if (providers.isArray()) {
                        // Process each provider
                        for (JsonNode provider : providers) {
                            JsonNode items = provider.get("items");
                            if (items != null && !items.isArray()) {
                                ((ObjectNode) provider).set("items", objectMapper.createArrayNode().add(items));
                            }
                        }
                    } else {
                        // Single provider
                        JsonNode items = providers.get("items");
                        if (items != null && !items.isArray()) {
                            ((ObjectNode) providers).set("items", objectMapper.createArrayNode().add(items));
                        }
                    }
                } else {
                    // No providers found
                    SearchResponseDto.Error error = new SearchResponseDto.Error();
                    error.setCode("NO_PROVIDERS_FOUND");
                    error.setMessage("No providers found in the catalog");
                    response.setError(error);
                }
                
                return response;
            }
        } catch (IOException e) {
            SearchResponseDto errorResponse = new SearchResponseDto();
            SearchResponseDto.Error error = new SearchResponseDto.Error();
            error.setCode("TRANSFORM_ERROR");
            error.setMessage("Failed to transform response: " + e.getMessage());
            errorResponse.setError(error);
            return errorResponse;
        }
    }
} 