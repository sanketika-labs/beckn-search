package org.beckn.search.model;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import jakarta.validation.ConstraintViolationException;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class SearchRequestDtoTest {
    private Validator validator;
    private ObjectMapper objectMapper;
    private String sampleJson;

    @BeforeEach
    void setUp() throws IOException {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
        objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES, true);
        objectMapper.configure(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES, true);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        objectMapper.registerModule(new ParameterNamesModule());
        
        // Read the sample JSON file
        ClassPathResource resource = new ClassPathResource("search_intent_body_sample.json");
        sampleJson = new String(Files.readAllBytes(resource.getFile().toPath()));
    }

    @Test
    void testValidRequestDeserialization() throws Exception {
        String json = """
            {
                "context": {
                    "domain": "retail",
                    "country": "IND",
                    "city": "std:080",
                    "bap_id": "buyer-app.beckn.org",
                    "bap_uri": "https://buyer-app.beckn.org",
                    "transaction_id": "12345",
                    "message_id": "12345",
                    "timestamp": "2025-04-15T10:30:00Z"
                },
                "message": {
                    "intent": {
                        "provider": {
                            "descriptor": {
                                "name": "Test Provider"
                            }
                        }
                    }
                }
            }
            """;

        SearchRequestDto request = objectMapper.readValue(json, SearchRequestDto.class);
        assertNotNull(request);
        assertNotNull(request.getContext());
        assertEquals("retail", request.getContext().getDomain());
        assertNotNull(request.getMessage());
        assertNotNull(request.getMessage().getIntent());
        assertNotNull(request.getMessage().getIntent().getProvider());
        assertNotNull(request.getMessage().getIntent().getProvider().getDescriptor());
        assertEquals("Test Provider", request.getMessage().getIntent().getProvider().getDescriptor().getName());
    }

    @Test
    void testInvalidRequest_MissingRequiredFields() {
        String json = """
            {
                "message": {
                    "intent": {}
                }
            }
            """;

        assertThrows(ConstraintViolationException.class, () -> {
            SearchRequestDto request = objectMapper.readValue(json, SearchRequestDto.class);
            var violations = validator.validate(request);
            if (!violations.isEmpty()) {
                throw new ConstraintViolationException(violations);
            }
        });
    }

    @Test
    void testNestedObjectValidation() {
        String json = """
            {
                "context": {
                    "domain": "retail",
                    "country": "IND",
                    "city": "std:080",
                    "bap_id": "buyer-app.beckn.org",
                    "bap_uri": "https://buyer-app.beckn.org",
                    "transaction_id": "12345",
                    "message_id": "12345",
                    "timestamp": "2025-04-15T10:30:00Z"
                },
                "message": {
                    "intent": {
                        "provider": {
                            "descriptor": {
                                "invalid_field": "test"
                            }
                        }
                    }
                }
            }
            """;

        assertThrows(UnrecognizedPropertyException.class, () -> {
            objectMapper.readValue(json, SearchRequestDto.class);
        });
    }
} 