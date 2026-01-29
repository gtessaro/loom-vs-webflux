package com.seuapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;

@ConfigurationProperties(prefix = "policy")
public record PolicyProperties(
        List<TimeoutRange> timeoutByRange,
        List<FallbackRange> fallbackByRange
) {
    public record TimeoutRange(long min, long max, long timeoutMs) {}
    public record FallbackRange(long min, long max, String defaultResult) {}
}
