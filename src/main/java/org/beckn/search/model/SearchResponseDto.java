package org.beckn.search.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class SearchResponseDto {
    private Context context;
    private Message message;

    @Data
    public static class Message {
        private Catalog catalog;
    }

    @Data
    public static class Catalog {
        private Descriptor descriptor;
        private JsonNode providers;
    }

    @Data
    public static class Descriptor {
        private String name;
        private String code;
        
        @JsonProperty("short_desc")
        private String shortDesc;
        
        @JsonProperty("long_desc")
        private String longDesc;
    }
} 