package com.seuapp.gateway.domain.mapper;

import com.seuapp.gateway.api.dto.GatewayRequest;
import com.seuapp.gateway.api.dto.GatewayResponse;
import com.seuapp.gateway.domain.external.ExternalRequest;
import com.seuapp.gateway.domain.external.ExternalResponse;
import org.springframework.stereotype.Component;

@Component
public class PayloadMapper {
    public ExternalRequest toExternal(GatewayRequest in){
        return new ExternalRequest(in.customerId(), String.valueOf(in.value()), in.data());
    }
    public GatewayResponse toOutbound(ExternalResponse ext, boolean fallbackApplied){
        return new GatewayResponse(ext.data(), fallbackApplied);
    }
    public GatewayResponse fallback(String fallbackValue){
        return new GatewayResponse(fallbackValue, true);
    }
}
