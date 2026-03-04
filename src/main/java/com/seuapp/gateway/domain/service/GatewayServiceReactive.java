package com.seuapp.gateway.domain.service;

import com.seuapp.gateway.api.dto.GatewayRequest;
import com.seuapp.gateway.api.dto.GatewayResponse;
import com.seuapp.gateway.domain.external.ExternalRequest;
import com.seuapp.gateway.domain.external.ExternalResponse;
import com.seuapp.gateway.domain.mapper.PayloadMapper;
import com.seuapp.gateway.domain.policy.RangePolicyEngine;
import com.seuapp.gateway.domain.port.ExternalSystemClientReactive;
import com.seuapp.gateway.domain.port.LogPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;

/**
 * Perfil "poc-webflux" (WebFlux).
 *
 * Para comparação justa com Loom, aplicamos o MESMO bulkhead + shed load.
 */
@Service
@Profile("poc-webflux")
public class GatewayServiceReactive {

    private final RangePolicyEngine policy;
    private final PayloadMapper mapper;
    private final ExternalSystemClientReactive externalClient;
    private final LogPublisher logPublisher;
    private final ExecutorService vtExec;
    private final Semaphore bulkhead;

    public GatewayServiceReactive(RangePolicyEngine policy,
                                 PayloadMapper mapper,
                                 ExternalSystemClientReactive externalClient,
                                 LogPublisher logPublisher,
                                 ExecutorService virtualThreadExecutor,
                                 Semaphore outboundBulkhead) {
        this.policy = policy;
        this.mapper = mapper;
        this.externalClient = externalClient;
        this.logPublisher = logPublisher;
        this.vtExec = virtualThreadExecutor;
        this.bulkhead = outboundBulkhead;
    }

    public Mono<GatewayResponse> process(GatewayRequest inbound) {
        long timeoutMs = policy.resolveTimeoutMs(inbound.value());
        Duration timeout = Duration.ofMillis(timeoutMs);
        ExternalRequest extReq = mapper.toExternal(inbound);

        return externalClient.call(extReq, timeout)
                .map(extResp -> {
                    GatewayResponse out = mapper.toOutbound(extResp, false);
                    publishAsync(inbound, timeoutMs, extReq, extResp, out, false);
                    return out;
                })
                .onErrorResume(ex -> {
                    GatewayResponse out = mapper.fallback(policy.resolveFallbackResult(inbound.value()));
                    publishAsync(inbound, timeoutMs, extReq, null, out, true);
                    return Mono.just(out);
                })
                .doFinally(sig -> bulkhead.release());
    }

    private void publishAsync(GatewayRequest inbound,
                              long timeoutMs,
                              ExternalRequest extReq,
                              ExternalResponse extResp,
                              GatewayResponse out,
                              boolean fallback) {
        vtExec.execute(() -> logPublisher.publish(
                new OperationLogEvent(inbound, timeoutMs, extReq, extResp, out, fallback)
        ));
    }
}
