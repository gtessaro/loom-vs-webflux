package com.seuapp.gateway.domain.service;

import com.seuapp.gateway.api.dto.GatewayRequest;
import com.seuapp.gateway.api.dto.GatewayResponse;
import com.seuapp.gateway.domain.external.ExternalResponse;
import com.seuapp.gateway.domain.mapper.PayloadMapper;
import com.seuapp.gateway.domain.policy.RangePolicyEngine;
import com.seuapp.gateway.domain.port.ExternalSystemClientBlocking;
import com.seuapp.gateway.domain.port.LogPublisher;
import com.seuapp.gateway.resilience.AdaptiveConcurrencyLimiter;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.Semaphore;

/**
 * Perfil "poc-loom" (MVC + Loom).
 *
 * Estratégia 1: requests "slow" (value>=700) são raros (<=0.5%) e devem cair em fallback.
 *
 * Anti-saturação (benchmark local):
 * - Bulkhead: limita concorrência outbound.
 * - Shed load: sem slot => fallback imediato (evita fila e timeouts artificiais).
 * - Deadline global: timeout vale para o request inteiro.
 */
@Service
@Profile("poc-loom")
public class GatewayServiceBlocking {

    private final RangePolicyEngine policy;
    private final PayloadMapper mapper;
    private final ExternalSystemClientBlocking externalClient;
    private final LogPublisher logPublisher;
    private final ExecutorService vtExec;
    private final Semaphore bulkhead;
    private final AdaptiveConcurrencyLimiter limiter;


    public GatewayServiceBlocking(RangePolicyEngine policy,
                                  PayloadMapper mapper,
                                  ExternalSystemClientBlocking externalClient,
                                  LogPublisher logPublisher,
                                  ExecutorService virtualThreadExecutor,
                                  Semaphore outboundBulkhead, AdaptiveConcurrencyLimiter limiter) {
        this.policy = policy;
        this.mapper = mapper;
        this.externalClient = externalClient;
        this.logPublisher = logPublisher;
        this.vtExec = virtualThreadExecutor;
        this.bulkhead = outboundBulkhead;
        this.limiter = limiter;
    }

    public GatewayResponse process(GatewayRequest inbound) {

        Future<ExternalResponse> fut = null;
        GatewayResponse out;
        ExternalResponse extResp = null;
        boolean fallbackApplied = false;


        long timeoutMs = policy.resolveTimeoutMs(inbound.value());
        Instant deadline = Instant.now().plusMillis(timeoutMs);

        var extReq = mapper.toExternal(inbound);

        if (!bulkhead.tryAcquire()) {
            out = mapper.fallback(policy.resolveFallbackResult(inbound.value()));
            publishLogAsync(inbound, timeoutMs, extReq, null, out, true);
            return out;
        }

        try {
            fut = vtExec.submit(() -> externalClient.call(extReq, Duration.ofMillis(timeoutMs)));

            long remainingMs = Duration.between(Instant.now(), deadline).toMillis();
            if (remainingMs <= 0) throw new TimeoutException("deadline passed before external call");

            extResp = fut.get(remainingMs, TimeUnit.MILLISECONDS);
            out = mapper.toOutbound(extResp, false);

        } catch (TimeoutException e) {
            if (fut != null) fut.cancel(true);
            fallbackApplied = true;
            out = mapper.fallback(policy.resolveFallbackResult(inbound.value()));

        } catch (ExecutionException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            fallbackApplied = true;
            out = mapper.fallback(policy.resolveFallbackResult(inbound.value()));

        } finally {
            bulkhead.release();
        }

        publishLogAsync(inbound, timeoutMs, extReq, extResp, out, fallbackApplied);
        return out;
    }

    private void publishLogAsync(GatewayRequest inbound,
                                 long timeoutMs,
                                 com.seuapp.gateway.domain.external.ExternalRequest extReq,
                                 ExternalResponse extResp,
                                 GatewayResponse out,
                                 boolean fallbackApplied) {
        vtExec.execute(() -> logPublisher.publish(
                new OperationLogEvent(inbound, timeoutMs, extReq, extResp, out, fallbackApplied)
        ));
    }
}
