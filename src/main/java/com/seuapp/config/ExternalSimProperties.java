package com.seuapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "external.sim")
public record ExternalSimProperties(
        long baseLatencyMs,
        long jitterMs,
        long timeoutAfterValue,
        long errorAfterValue
) {}
