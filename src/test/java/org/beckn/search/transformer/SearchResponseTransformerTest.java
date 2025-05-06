package org.beckn.search.transformer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.beckn.search.model.SearchResponseDto;
import org.beckn.search.transformer.SearchResponseTransformer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class SearchResponseTransformerTest {
    private SearchResponseTransformer transformer;
    private ObjectMapper objectMapper;
    private String catalogJson;

    @BeforeEach
    void setUp() throws IOException {
        objectMapper = new ObjectMapper();
        transformer = new SearchResponseTransformer(objectMapper);
        
        ClassPathResource resource = new ClassPathResource("beckn_catalog.json");
        catalogJson = new String(Files.readAllBytes(resource.getFile().toPath()));
    }

    @Test
    void testRawCatalogExtraction() throws IOException {
        String rawCatalog = transformer.extractRawCatalog(catalogJson);
        assertNotNull(rawCatalog);
        assertTrue(rawCatalog.contains("EcoCharge-Retail-Catalog"));
    }

    @Test
    void testJsonStringToObjectConversion() throws IOException {
        SearchResponseDto response = transformer.transformToResponse(catalogJson);
        
        assertNotNull(response);
        assertNotNull(response.getMessage());
        assertNotNull(response.getMessage().getCatalog());
        assertNotNull(response.getMessage().getCatalog().getProviders());
        assertEquals("EcoCharge-Retail-Catalog", response.getMessage().getCatalog().getDescriptor().getName());
    }

    @Test
    void testErrorHandling_NullOrEmptyInput() {
        // Test null input
        Exception nullException = assertThrows(IllegalArgumentException.class, () -> {
            transformer.transformToResponse(null);
        });
        assertTrue(nullException.getMessage().contains("Raw catalog cannot be null or empty"));

        // Test empty input
        Exception emptyException = assertThrows(IllegalArgumentException.class, () -> {
            transformer.transformToResponse("");
        });
        assertTrue(emptyException.getMessage().contains("Raw catalog cannot be null or empty"));
    }

    @Test
    void testErrorHandling_MalformedJson() {
        String invalidJson = "{invalid: json}";
        
        Exception exception = assertThrows(RuntimeException.class, () -> {
            transformer.transformToResponse(invalidJson);
        });
        
        assertTrue(exception.getMessage().contains("Failed to transform response"));
    }
} 