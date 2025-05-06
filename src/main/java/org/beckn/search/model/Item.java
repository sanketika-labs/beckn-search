package org.beckn.search.model;

import jakarta.validation.Valid;
import lombok.Data;

@Data
public class Item {
    @Valid
    private Descriptor descriptor;
    
    @Valid
    private Price price;
    
    private String rating;
} 