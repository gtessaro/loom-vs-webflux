package com.seuapp.gateway.domain.service;

import com.seuapp.gateway.api.dto.GatewayRequest;
import com.seuapp.gateway.domain.external.ExternalRequest;
import com.seuapp.gateway.domain.external.ExternalResponse;

public record OperationLogEvent(
        GatewayRequest inbound,
        long timeoutMsApplied,
        ExternalRequest outboundToExternal,
        ExternalResponse responseFromExternal,
        Object responseToCaller,
        boolean fallbackApplied
) {}
