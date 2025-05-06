package org.beckn.search.api;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.beckn.search.elasticsearch.SearchService;
import org.beckn.search.model.SearchRequestDto;
import org.beckn.search.model.SearchResponseDto;
import org.beckn.search.validation.SearchRequestValidator;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonInclude;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@Validated
public class SearchController {
    private static final Logger logger = LoggerFactory.getLogger(SearchController.class);
    private final SearchService searchService;
    private final SearchRequestValidator requestValidator;
    private final ObjectMapper objectMapper;

    @Autowired
    public SearchController(SearchService searchService,
                          SearchRequestValidator requestValidator,
                          ObjectMapper objectMapper) {
        this.searchService = searchService;
        this.requestValidator = requestValidator;
        this.objectMapper = objectMapper.copy().setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    @PostMapping(value = "/search", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SearchResponseDto> search(
            @Valid @RequestBody SearchRequestDto request,
            @RequestParam(value = "operator", defaultValue = "AND") String operator) throws IOException {
        // Log the incoming request
        try {
            logger.info("Search request received: {}", objectMapper.writeValueAsString(request));
        } catch (Exception e) {
            logger.warn("Failed to log request body", e);
        }

        // Validate the request
        requestValidator.validate(request);

        // Extract pagination parameters from the message
        int pageNum = 0;
        int pageSize = 10;
        if (request.getMessage() != null && request.getMessage().getIntent() != null) {
            pageNum = request.getMessage().getIntent().getPage() != null ? 
                request.getMessage().getIntent().getPage() : 0;
            pageSize = request.getMessage().getIntent().getLimit() != null ? 
                request.getMessage().getIntent().getLimit() : 10;
        }
        
        // Get response directly from service
        SearchResponseDto responseDto = searchService.searchAndGetResponse(request, operator);

        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(responseDto);
    }
} 