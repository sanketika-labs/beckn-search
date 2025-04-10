package org.beckn.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class SearchRequest {
    private String id;
    private String ver;
    private OffsetDateTime ts;
    private Params params;
    private Request request;

    @Getter
    @Setter
    public static class Params {
        private UUID msgid;
    }

    @Getter
    @Setter
    public static class Request {
        private Context context;
        private Search search;
    }

    @Getter
    @Setter
    public static class Context {
        private String domain;
    }

    @Getter
    @Setter
    public static class Search {
        private String text;
        @JsonProperty("geo_spatial")
        private GeoSpatial geoSpatial;
        private List<Filter> filters;
        private Page page;
    }

    @Getter
    @Setter
    public static class GeoSpatial {
        private String distance;
        private String unit;
        private Location location;
    }

    @Getter
    @Setter
    public static class Location {
        private double lat;
        private double lon;
    }

    @Getter
    @Setter
    public static class Filter {
        private String type;
        private List<Field> fields;
    }

    @Getter
    @Setter
    public static class Field {
        private String name;
        private String op;
        private Object value;
        private String type;
        private List<Field> fields;
    }

    @Getter
    @Setter
    public static class Page {
        private int from;
        private int size;
    }
} 