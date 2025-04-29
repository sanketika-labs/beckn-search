package org.beckn.search.api;

import jakarta.validation.Valid;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.beckn.search.elasticsearch.SearchService;
import org.beckn.search.model.SearchRequestDto;
import org.beckn.search.model.SearchResponseDto;
import org.beckn.search.transformer.SearchResponseTransformer;
import org.beckn.search.validation.SearchRequestValidator;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import java.io.IOException;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@Validated
public class SearchController {
    private static final Logger logger = LoggerFactory.getLogger(SearchController.class);
    private final SearchService searchService;
    private final SearchResponseTransformer responseTransformer;
    private final SearchRequestValidator requestValidator;

    @Autowired
    public SearchController(SearchService searchService,
                          SearchResponseTransformer responseTransformer,
                          SearchRequestValidator requestValidator) {
        this.searchService = searchService;
        this.responseTransformer = responseTransformer;
        this.requestValidator = requestValidator;
    }

    @PostMapping(value = "/search", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> search(@Valid @RequestBody SearchRequestDto request) {
        try {
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
            
            // Get raw catalog and transform to response
            String rawCatalog = searchService.searchAndGetRawCatalog(request, pageNum, pageSize);
            SearchResponseDto responseDto = responseTransformer.transformToResponse(rawCatalog);
            
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(responseDto);
                
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request parameters: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse("Invalid request: " + e.getMessage()));
                
        } catch (IOException e) {
            logger.error("Error communicating with Elasticsearch: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse("Search service temporarily unavailable"));
                
        } catch (Exception e) {
            logger.error("Unexpected error during search: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse("Internal server error"));
        }
    }
}

@Data
class ErrorResponse {
    private final String error;
} 