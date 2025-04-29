package org.beckn.search.model;

import jakarta.validation.Valid;
import lombok.Data;

@Data
public class Provider {
    private String id;
    
    @Valid
    private Descriptor descriptor;
    
    private java.util.List<Category> categories;
} 