package org.beckn.search.model;

import jakarta.validation.Valid;
import lombok.Data;
import java.util.List;

@Data
public class Provider {
    private String id;
    
    @Valid
    private Descriptor descriptor;
    
    private List<Category> categories;
    
    private List<Location> locations;
    
    private List<Fulfillment> fulfillments;
} 