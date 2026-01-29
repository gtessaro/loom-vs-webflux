package com.seuapp.domain.service;

import com.seuapp.api.dto.GatewayRequest;
import com.seuapp.domain.external.ExternalRequest;
import com.seuapp.domain.external.ExternalResponse;

public record OperationLogEvent(
        GatewayRequest inbound,
        long timeoutMsApplied,
        ExternalRequest outboundToExternal,
        ExternalResponse responseFromExternal,
        Object responseToCaller,
        boolean fallbackApplied
) {}
