package org.beckn.search.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.Data;
import java.util.List;

@Data
public class Intent {
    @Valid
    private Provider provider;
    
    @Valid
    private List<Provider> providers;
    
    @Valid
    private List<Item> items;

    @Min(value = 0, message = "Page number must be 0 or greater")
    private Integer page;

    @Min(value = 1, message = "Page size must be at least 1")
    private Integer limit;
} 