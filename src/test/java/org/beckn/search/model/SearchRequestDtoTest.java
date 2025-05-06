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
    void testValidRequestDeserialization() throws IOException {
        String validJson = """
            {
                "context": {
                    "domain": "retail",
                    "location": {
                        "country": {
                            "name": "India",
                            "code": "IND"
                        },
                        "city": {
                            "name": "Bangalore",
                            "code": "std:080"
                        }
                    },
                    "bap_id": "buyer-app.beckn.org",
                    "bap_uri": "https://buyer-app.beckn.org",
                    "transaction_id": "12345678-aaaa-bbbb-cccc-1234567890ab",
                    "message_id": "abcdef12-3456-7890-abcd-ef1234567890",
                    "timestamp": "2025-04-15T10:30:00Z"
                },
                "message": {
                    "intent": {
                        "provider": {
                            "id": "provider1.beckn.org",
                            "descriptor": {
                                "name": "provider1"
                            },
                            "categories": [
                                {
                                    "id": "grocery"
                                }
                            ]
                        },
                        "items": [
                            {
                                "descriptor": {
                                    "name": "milk",
                                    "code": "milk",
                                    "short_desc": "string",
                                    "long_desc": "string"
                                },
                                "price": {
                                    "currency": "INR",
                                    "value": "65.87",
                                    "estimated_value": "70.48",
                                    "computed_value": "75.78",
                                    "listed_value": "73.17",
                                    "offered_value": "72.67",
                                    "minimum_value": "71.40",
                                    "maximum_value": "73.20"
                                },
                                "rating": "4.9"
                            }
                        ]
                    }
                }
            }
            """;

        SearchRequestDto request = objectMapper.readValue(validJson, SearchRequestDto.class);
        assertNotNull(request);
        assertNotNull(request.getContext());
        assertEquals("retail", request.getContext().getDomain());
        assertNotNull(request.getContext().getLocation());
        assertEquals("India", request.getContext().getLocation().getCountry().getName());
        assertEquals("IND", request.getContext().getLocation().getCountry().getCode());
        assertEquals("Bangalore", request.getContext().getLocation().getCity().getName());
        assertEquals("std:080", request.getContext().getLocation().getCity().getCode());
        assertEquals("buyer-app.beckn.org", request.getContext().getBapId());
        assertEquals("https://buyer-app.beckn.org", request.getContext().getBapUri());
        assertEquals("12345678-aaaa-bbbb-cccc-1234567890ab", request.getContext().getTransactionId());
        assertEquals("abcdef12-3456-7890-abcd-ef1234567890", request.getContext().getMessageId());
        assertEquals("2025-04-15T10:30:00Z", request.getContext().getTimestamp());
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
                    "location": {
                        "country": {
                            "name": "India",
                            "code": "IND"
                        },
                        "city": {
                            "name": "Bangalore",
                            "code": "std:080"
                        }
                    },
                    "bap_id": "buyer-app.beckn.org",
                    "bap_uri": "https://buyer-app.beckn.org",
                    "transaction_id": "98765432-dddd-eeee-ffff-0987654321fe",
                    "message_id": "fedcba98-7654-3210-fedc-ba9876543210",
                    "timestamp": "2025-04-15T10:30:00Z"
                },
                "message": {
                    "intent": {
                        "provider": {
                            "descriptor": {
                                "invalid_field": "test",
                                "another_invalid": "value"
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