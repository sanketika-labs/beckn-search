package org.beckn.search.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class Context {
    @NotNull
    private String domain;
    
    private Location location;
    
    @JsonProperty("bap_id")
    private String bapId;
    
    @JsonProperty("bap_uri")
    private String bapUri;
    
    @JsonProperty("transaction_id")
    private String transactionId;
    
    @JsonProperty("message_id")
    private String messageId;
    
    private String timestamp;

    private String action;

    @JsonProperty("core_version")
    private String coreVersion;
} 