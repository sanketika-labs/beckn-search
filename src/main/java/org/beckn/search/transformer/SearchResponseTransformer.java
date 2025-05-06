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
            throw new IllegalArgumentException("Raw catalog cannot be null or empty");
        }

        try {
            JsonNode catalogNode = objectMapper.readTree(rawCatalog);
            
            // If we have an array of raw catalogs
            if (catalogNode.isArray()) {
                if (catalogNode.size() == 0) {
                    // Empty array
                    SearchResponseDto errorResponse = new SearchResponseDto();
                    SearchResponseDto.Error error = new SearchResponseDto.Error();
                    error.setCode("NO_SEARCH_RESULTS");
                    error.setMessage("No results found for the search criteria");
                    errorResponse.setError(error);
                    return errorResponse;
                }
                
                // Create a new response
                SearchResponseDto response = new SearchResponseDto();
                SearchResponseDto.Message message = new SearchResponseDto.Message();
                SearchResponseDto.Catalog catalog = new SearchResponseDto.Catalog();
                
                // Get the first catalog's descriptor if available
                JsonNode firstCatalog = catalogNode.get(0);
                if (firstCatalog != null) {
                    JsonNode firstCatalogJson = objectMapper.readTree(firstCatalog.asText());
                    if (firstCatalogJson.has("message") && 
                        firstCatalogJson.get("message").has("catalog") && 
                        firstCatalogJson.get("message").get("catalog").has("descriptor")) {
                        catalog.setDescriptor(objectMapper.treeToValue(
                            firstCatalogJson.get("message").get("catalog").get("descriptor"),
                            SearchResponseDto.Descriptor.class));
                    }
                }
                
                // Create providers array
                ArrayNode providers = objectMapper.createArrayNode();
                
                // Process each raw catalog
                for (JsonNode rawCatalogNode : catalogNode) {
                    // Parse each raw catalog
                    JsonNode catalogJson = objectMapper.readTree(rawCatalogNode.asText());
                    
                    // Get providers from this catalog and add them to the array
                    if (catalogJson.has("message") && 
                        catalogJson.get("message").has("catalog") && 
                        catalogJson.get("message").get("catalog").has("providers")) {
                        
                        JsonNode catalogProviders = catalogJson.get("message").get("catalog").get("providers");
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
            } else {
                // Single raw catalog
                JsonNode catalogJson = objectMapper.readTree(rawCatalog);
                SearchResponseDto response = new SearchResponseDto();
                SearchResponseDto.Message message = new SearchResponseDto.Message();
                SearchResponseDto.Catalog catalog = new SearchResponseDto.Catalog();
                
                // Get catalog descriptor if available
                if (catalogJson.has("message") && 
                    catalogJson.get("message").has("catalog") && 
                    catalogJson.get("message").get("catalog").has("descriptor")) {
                    catalog.setDescriptor(objectMapper.treeToValue(
                        catalogJson.get("message").get("catalog").get("descriptor"),
                        SearchResponseDto.Descriptor.class));
                }
                
                // Get providers
                if (catalogJson.has("message") && 
                    catalogJson.get("message").has("catalog") && 
                    catalogJson.get("message").get("catalog").has("providers")) {
                    JsonNode providers = catalogJson.get("message").get("catalog").get("providers");
                    if (providers.isArray()) {
                        // Process each provider
                        for (JsonNode provider : providers) {
                            JsonNode items = provider.get("items");
                            if (items != null && !items.isArray()) {
                                ((ObjectNode) provider).set("items", objectMapper.createArrayNode().add(items));
                            }
                        }
                        catalog.setProviders(providers);
                    } else {
                        // Single provider
                        JsonNode items = providers.get("items");
                        if (items != null && !items.isArray()) {
                            ((ObjectNode) providers).set("items", objectMapper.createArrayNode().add(items));
                        }
                        
                        // Convert single provider to array
                        ArrayNode providersArray = objectMapper.createArrayNode();
                        providersArray.add(providers);
                        catalog.setProviders(providersArray);
                    }
                } else {
                    // No providers found
                    SearchResponseDto.Error error = new SearchResponseDto.Error();
                    error.setCode("NO_PROVIDERS_FOUND");
                    error.setMessage("No providers found in the catalog");
                    response.setError(error);
                }
                
                message.setCatalog(catalog);
                response.setMessage(message);
                return response;
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to transform response: " + e.getMessage(), e);
        }
    }
} 