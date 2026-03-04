package com.seuapp.gateway.domain.external;

import java.util.Map;

public record ExternalRequest(String customerId, String externalValue, Map<String, Object> payload) {}
