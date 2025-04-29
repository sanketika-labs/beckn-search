package org.beckn.search.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Descriptor {
    private String name;
    private String code;
    
    @JsonProperty("short_desc")
    private String shortDesc;
    
    @JsonProperty("long_desc")
    private String longDesc;
} 