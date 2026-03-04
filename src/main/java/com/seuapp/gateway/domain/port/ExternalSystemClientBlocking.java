package com.seuapp.gateway.domain.port;

import com.seuapp.gateway.domain.external.ExternalRequest;
import com.seuapp.gateway.domain.external.ExternalResponse;

import java.time.Duration;

public interface ExternalSystemClientBlocking {
    ExternalResponse call(ExternalRequest request, Duration timeout) throws Exception;
}
