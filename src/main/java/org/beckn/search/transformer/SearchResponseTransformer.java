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
            SearchResponseDto response = new SearchResponseDto();

            // Create message and catalog
            SearchResponseDto.Message message = new SearchResponseDto.Message();
            SearchResponseDto.Catalog catalog = new SearchResponseDto.Catalog();
            
            // Get the raw catalog node
            if (!catalogNode.has("message")) {
                throw new IllegalArgumentException("Missing required field: message");
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