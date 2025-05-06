package org.beckn.search.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Location {
    private String id;
    private Descriptor descriptor;
    private String address;
    private City city;
    private String district;
    private State state;
    private Country country;
    
    @JsonProperty("area_code")
    private String areaCode;
    
    @JsonProperty("gps")
    private String gps;

    @Data
    public static class City {
        private String name;
        private String code;
    }

    @Data
    public static class State {
        private String name;
        private String code;
    }

    @Data
    public static class Country {
        private String name;
        private String code;
    }
} 