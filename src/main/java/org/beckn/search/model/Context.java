package org.beckn.search.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class Context {
    @NotNull
    private String domain;
    
    @NotNull
    private String country;
    
    @NotNull
    private String city;
    
    @NotNull
    @JsonProperty("bap_id")
    private String bapId;
    
    @NotNull
    @JsonProperty("bap_uri")
    private String bapUri;
    
    @NotNull
    @JsonProperty("transaction_id")
    private String transactionId;
    
    @NotNull
    @JsonProperty("message_id")
    private String messageId;
    
    @NotNull
    private String timestamp;

    private String action;

    @JsonProperty("core_version")
    private String coreVersion;
} 