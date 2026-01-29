package com.seuapp.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * POC: payload grande representado como Map.
 * Em produção você pode trocar por um DTO tipado com ~200 campos.
 */
public record GatewayRequest(
        @NotNull Long value,
        String customerId,
        @NotNull Map<String, Object> data
) {}
