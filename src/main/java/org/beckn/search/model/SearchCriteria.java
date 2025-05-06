package org.beckn.search.model;

import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class SearchCriteria {
    private String domain;
    private Location location;
    private Fulfillment fulfillment;
    private Intent intent;
} 