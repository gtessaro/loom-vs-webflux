package com.seuapp.api;

import com.seuapp.api.dto.GatewayRequest;
import com.seuapp.api.dto.GatewayResponse;
import com.seuapp.domain.service.GatewayServiceReactive;
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
        if (debugThread) {
            return Mono.just("Thread=" + Thread.currentThread());
        }
        return service.process(request);
    }
}
