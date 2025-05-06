package org.beckn.search.api;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.beckn.search.model.SearchResponseDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.MediaType;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ResponseBody;
import java.io.IOException;
import java.util.stream.Collectors;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseBody
    public ResponseEntity<SearchResponseDto> handleValidationExceptions(MethodArgumentNotValidException ex) {
        String errorMessage = ex.getBindingResult()
                .getAllErrors()
                .stream()
                .map(error -> {
                    if (error instanceof FieldError) {
                        return ((FieldError) error).getField() + ": " + error.getDefaultMessage();
                    }
                    return error.getDefaultMessage();
                })
                .collect(Collectors.joining(", "));

        SearchResponseDto errorResponse = new SearchResponseDto();
        SearchResponseDto.Error error = new SearchResponseDto.Error();
        error.setCode("INVALID_REQUEST");
        error.setMessage(errorMessage);
        errorResponse.setError(error);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(errorResponse);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseBody
    public ResponseEntity<SearchResponseDto> handleMessageNotReadableException(HttpMessageNotReadableException ex) {
        SearchResponseDto errorResponse = new SearchResponseDto();
        SearchResponseDto.Error error = new SearchResponseDto.Error();
        error.setCode("INVALID_REQUEST");

        // Get the specific cause to provide better error messages
        Throwable cause = ex.getCause();
        if (cause instanceof JsonParseException) {
            error.setMessage("Invalid JSON format: " + cause.getMessage());
        } else if (cause instanceof InvalidFormatException) {
            InvalidFormatException ife = (InvalidFormatException) cause;
            error.setMessage("Invalid value for field '" + ife.getPath().get(0).getFieldName() + "': " + cause.getMessage());
        } else if (cause instanceof MismatchedInputException) {
            MismatchedInputException mie = (MismatchedInputException) cause;
            error.setMessage("Invalid request structure: " + (mie.getPath().isEmpty() ? mie.getMessage() : 
                "Invalid value for field '" + mie.getPath().get(0).getFieldName() + "'"));
        } else {
            error.setMessage("Invalid request format: " + ex.getMessage());
        }

        errorResponse.setError(error);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(errorResponse);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseBody
    public ResponseEntity<SearchResponseDto> handleIllegalArgumentException(IllegalArgumentException ex) {
        SearchResponseDto errorResponse = new SearchResponseDto();
        SearchResponseDto.Error error = new SearchResponseDto.Error();
        error.setCode("INVALID_REQUEST");
        error.setMessage(ex.getMessage());
        errorResponse.setError(error);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(errorResponse);
    }

    @ExceptionHandler(IOException.class)
    @ResponseBody
    public ResponseEntity<SearchResponseDto> handleIOException(IOException ex) {
        SearchResponseDto errorResponse = new SearchResponseDto();
        SearchResponseDto.Error error = new SearchResponseDto.Error();
        error.setCode("SERVICE_UNAVAILABLE");
        error.setMessage("Search service temporarily unavailable");
        errorResponse.setError(error);

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    @ResponseBody
    public ResponseEntity<SearchResponseDto> handleAllExceptions(Exception ex) {
        SearchResponseDto errorResponse = new SearchResponseDto();
        SearchResponseDto.Error error = new SearchResponseDto.Error();
        error.setCode("INTERNAL_ERROR");
        error.setMessage("An unexpected error occurred: " + ex.getMessage());
        errorResponse.setError(error);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(errorResponse);
    }
} 