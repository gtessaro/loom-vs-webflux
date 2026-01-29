package com.seuapp.domain.service;

import com.seuapp.api.dto.GatewayRequest;
import com.seuapp.api.dto.GatewayResponse;
import com.seuapp.domain.external.ExternalResponse;
import com.seuapp.domain.mapper.PayloadMapper;
import com.seuapp.domain.policy.RangePolicyEngine;
import com.seuapp.domain.port.ExternalSystemClient;
import com.seuapp.domain.port.LogPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.concurrent.ExecutorService;

/**
 * Implementação REACTIVE (WebFlux).
 * Como o mock externo é bloqueante, usamos fromCallable + boundedElastic.
 * Em produção reativa, você trocaria ExternalSystemClient por WebClient non-blocking.
 */
@Service
@Profile("poc-webflux")
public class GatewayServiceReactive {

    private final RangePolicyEngine policy;
    private final PayloadMapper mapper;
    private final ExternalSystemClient externalClient;
    private final LogPublisher logPublisher;
    private final ExecutorService vtExec;

    public GatewayServiceReactive(RangePolicyEngine policy,
                                  PayloadMapper mapper,
                                  ExternalSystemClient externalClient,
                                  LogPublisher logPublisher,
                                  ExecutorService virtualThreadExecutor) {
        this.policy = policy;
        this.mapper = mapper;
        this.externalClient = externalClient;
        this.logPublisher = logPublisher;
        this.vtExec = virtualThreadExecutor;
    }

    public Mono<GatewayResponse> process(GatewayRequest inbound) {
        long timeoutMs = policy.resolveTimeoutMs(inbound.value());
        var extReq = mapper.toExternal(inbound);

        Mono<ExternalResponse> externalMono =
                Mono.fromCallable(() -> externalClient.call(extReq))
                        .subscribeOn(Schedulers.boundedElastic())
                        .timeout(Duration.ofMillis(timeoutMs));

        return externalMono
                .map(extResp -> {
                    GatewayResponse out = mapper.toOutbound(extResp, false);
                    publishAsync(inbound, timeoutMs, extReq, extResp, out, false);
                    return out;
                })
                .onErrorResume(ex -> {
                    GatewayResponse out = mapper.fallback(policy.resolveFallbackResult(inbound.value()));
                    publishAsync(inbound, timeoutMs, extReq, null, out, true);
                    return Mono.just(out);
                });
    }

    private void publishAsync(GatewayRequest inbound,
                              long timeoutMs,
                              com.seuapp.domain.external.ExternalRequest extReq,
                              ExternalResponse extResp,
                              GatewayResponse out,
                              boolean fallback) {
        vtExec.execute(() -> logPublisher.publish(
                new OperationLogEvent(inbound, timeoutMs, extReq, extResp, out, fallback)
        ));
    }
}
