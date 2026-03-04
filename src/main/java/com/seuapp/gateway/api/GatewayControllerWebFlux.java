package com.seuapp.gateway.api;

import com.seuapp.gateway.api.dto.GatewayRequest;
import com.seuapp.gateway.domain.service.GatewayServiceReactive;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/v1/gateway")
@Profile("poc-webflux")
public class GatewayControllerWebFlux {

    private final GatewayServiceReactive service;

    public GatewayControllerWebFlux(GatewayServiceReactive service) {
        this.service = service;
    }

    @PostMapping("/process")
    public Mono<?> process(@RequestBody @Valid GatewayRequest request,
                           @RequestParam(name="debugThread", defaultValue="false") boolean debugThread) {
        if (debugThread) return Mono.just("Thread=" + Thread.currentThread());
        return service.process(request);
    }
}
