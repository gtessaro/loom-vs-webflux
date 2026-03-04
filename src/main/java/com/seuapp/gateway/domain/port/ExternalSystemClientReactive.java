package com.seuapp.gateway.domain.port;

import com.seuapp.gateway.domain.external.ExternalRequest;
import com.seuapp.gateway.domain.external.ExternalResponse;
import reactor.core.publisher.Mono;

import java.time.Duration;

public interface ExternalSystemClientReactive {
    Mono<ExternalResponse> call(ExternalRequest request, Duration timeout);
}
