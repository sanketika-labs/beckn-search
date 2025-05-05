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
            
            // If we have an array of raw catalogs, combine them
            if (catalogNode.isArray()) {
                ArrayNode combinedProviders = objectMapper.createArrayNode();
                catalogNode.forEach(rawCatalogStr -> {
                    try {
                        JsonNode rawCatalogNode = objectMapper.readTree(rawCatalogStr.asText());
                            
                        if (rawCatalogNode.has("message") && 
                            rawCatalogNode.get("message").has("catalog") && 
                            rawCatalogNode.get("message").get("catalog").has("providers")) {
                            JsonNode providers = rawCatalogNode.get("message").get("catalog").get("providers");
                            if (providers.isArray()) {
                                providers.forEach(provider -> {
                                    // Create a new provider with items
                                    ObjectNode newProvider = objectMapper.createObjectNode();
                                    // Copy all fields from the original provider
                                    provider.fields().forEachRemaining(field -> 
                                        newProvider.set(field.getKey(), field.getValue()));
                                    
                                    // Add items array if not present
                                    if (!newProvider.has("items")) {
                                        newProvider.putArray("items");
                                    }
                                    
                                    // Add the item from the raw catalog
                                    if (rawCatalogNode.get("message").get("catalog").has("items")) {
                                        JsonNode items = rawCatalogNode.get("message").get("catalog").get("items");
                                        if (items.isArray()) {
                                            ((ArrayNode) newProvider.get("items")).addAll((ArrayNode) items);
                                        }
                                    }
                                    
                                    combinedProviders.add(newProvider);
                                });
                            }
                        } else {
                            // If no providers found, create a new provider from the raw catalog
                            ObjectNode newProvider = objectMapper.createObjectNode();
                            if (rawCatalogNode.has("provider_descriptor_name")) {
                                ObjectNode descriptor = objectMapper.createObjectNode();
                                descriptor.put("name", rawCatalogNode.get("provider_descriptor_name").asText());
                                newProvider.set("descriptor", descriptor);
                            }
                            
                            // Add items array
                            ArrayNode items = newProvider.putArray("items");
                            if (rawCatalogNode.has("items_descriptor_name") && rawCatalogNode.get("items_descriptor_name").isArray()) {
                                rawCatalogNode.get("items_descriptor_name").forEach(itemName -> {
                                    ObjectNode item = objectMapper.createObjectNode();
                                    ObjectNode itemDescriptor = objectMapper.createObjectNode();
                                    itemDescriptor.put("name", itemName.asText());
                                    item.set("descriptor", itemDescriptor);
                                    items.add(item);
                                });
                            }
                            
                            combinedProviders.add(newProvider);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to parse raw catalog", e);
                    }
                });
                
                // Create a combined catalog structure
                ObjectNode combined = objectMapper.createObjectNode();
                ObjectNode messageNode = combined.putObject("message");
                ObjectNode catalogObj = messageNode.putObject("catalog");
                catalogObj.set("descriptor", objectMapper.createObjectNode()
                    .put("name", "Combined Catalog")
                    .put("short_desc", "Combined search results"));
                catalogObj.set("providers", combinedProviders);
                catalogNode = combined;
            }

            SearchResponseDto response = new SearchResponseDto();

            // Create message and catalog
            SearchResponseDto.Message message = new SearchResponseDto.Message();
            SearchResponseDto.Catalog catalog = new SearchResponseDto.Catalog();
            
            // Get the raw catalog node
            if (!catalogNode.has("message")) {
                // If no message node, create a new catalog structure
                ObjectNode newCatalog = objectMapper.createObjectNode();
                ObjectNode messageObj = newCatalog.putObject("message");
                ObjectNode catalogObj = messageObj.putObject("catalog");
                catalogObj.set("descriptor", objectMapper.createObjectNode()
                    .put("name", "Search Results")
                    .put("short_desc", "Search results catalog"));
                catalogObj.set("providers", catalogNode.isArray() ? catalogNode : objectMapper.createArrayNode().add(catalogNode));
                catalogNode = newCatalog;
            }
            
            JsonNode messageNode = catalogNode.get("message");
            if (!messageNode.has("catalog")) {
                throw new IllegalArgumentException("Missing required field: message.catalog");
            }
            JsonNode rawCatalogNode = messageNode.get("catalog");
            
            // Set descriptor if present
            if (rawCatalogNode.has("descriptor")) {
                JsonNode descriptorNode = rawCatalogNode.get("descriptor");
                if (descriptorNode != null && !descriptorNode.isNull()) {
                    SearchResponseDto.Descriptor descriptor = objectMapper.treeToValue(
                        descriptorNode, 
                        SearchResponseDto.Descriptor.class
                    );
                    catalog.setDescriptor(descriptor);
                }
            }
            
            // Set providers if present
            if (rawCatalogNode.has("providers")) {
                catalog.setProviders(rawCatalogNode.get("providers"));
            }

            message.setCatalog(catalog);
            response.setMessage(message);
            
            return response;
        } catch (IOException e) {
            throw new RuntimeException("Failed to transform response", e);
        }
    }
} 