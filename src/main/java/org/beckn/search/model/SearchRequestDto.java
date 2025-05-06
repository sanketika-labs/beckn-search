package org.beckn.search.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.validation.annotation.Validated;

@Validated
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchRequestDto {
    @NotNull(message = "context is required")
    @Valid
    @JsonProperty(required = true)
    private Context context;
    
    @NotNull(message = "message is required")
    @Valid
    @JsonProperty(required = true)
    private Message message;
} 