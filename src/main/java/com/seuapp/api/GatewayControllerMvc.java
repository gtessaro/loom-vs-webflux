package com.seuapp.api;

import com.seuapp.api.dto.GatewayRequest;
import com.seuapp.domain.service.GatewayServiceBlocking;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/gateway")
@Profile("poc-loom")
public class GatewayControllerMvc {

    private final GatewayServiceBlocking service;

    public GatewayControllerMvc(GatewayServiceBlocking service) {
        this.service = service;
    }

    @PostMapping("/process")
    public Object process(@RequestBody @Valid GatewayRequest request,
                          @RequestParam(name="debugThread", defaultValue="false") boolean debugThread) {
        if (debugThread) {
            return "Thread=" + Thread.currentThread();
        }
        return service.process(request);
    }
}
