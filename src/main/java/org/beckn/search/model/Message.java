package org.beckn.search.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class Message {
    @NotNull
    @Valid
    private Intent intent;
} 