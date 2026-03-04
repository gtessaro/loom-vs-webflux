package com.seuapp.gateway.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record GatewayRequest(
        @NotNull Long value,
        String customerId,
        @NotNull Map<String, Object> data
) {}
