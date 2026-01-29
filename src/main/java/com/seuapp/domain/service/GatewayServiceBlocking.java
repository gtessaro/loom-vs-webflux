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

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.*;

/**
 * Implementação BLOQUEANTE (boa para MVC + Loom).
 * Usa Future.get(timeout) para aplicar deadline dinâmico.
 */
@Service
@Profile("poc-loom")
public class GatewayServiceBlocking {

    private final RangePolicyEngine policy;
    private final PayloadMapper mapper;
    private final ExternalSystemClient externalClient;
    private final LogPublisher logPublisher;
    private final ExecutorService vtExec;

    public GatewayServiceBlocking(RangePolicyEngine policy,
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

    public GatewayResponse process(GatewayRequest inbound) {
        long timeoutMs = policy.resolveTimeoutMs(inbound.value());
        Instant deadline = Instant.now().plusMillis(timeoutMs);

        var extReq = mapper.toExternal(inbound);
        Future<ExternalResponse> externalFuture = vtExec.submit(() -> externalClient.call(extReq));

        GatewayResponse out;
        ExternalResponse extResp = null;
        boolean fallbackApplied = false;

        try {
            long remainingMs = Duration.between(Instant.now(), deadline).toMillis();
            if (remainingMs <= 0) throw new TimeoutException("deadline passed");

            extResp = externalFuture.get(remainingMs, TimeUnit.MILLISECONDS);
            out = mapper.toOutbound(extResp, false);
        } catch (TimeoutException e) {
            externalFuture.cancel(true);
            fallbackApplied = true;
            out = mapper.fallback(policy.resolveFallbackResult(inbound.value()));
        } catch (ExecutionException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            fallbackApplied = true;
            out = mapper.fallback(policy.resolveFallbackResult(inbound.value()));
        }

        boolean finalFallback = fallbackApplied;
        ExternalResponse finalExtResp = extResp;
        GatewayResponse finalOut = out;

        // "publica no tópico" sem bloquear o request
        vtExec.execute(() -> logPublisher.publish(
                new OperationLogEvent(inbound, timeoutMs, extReq, finalExtResp, finalOut, finalFallback)
        ));

        return out;
    }
}
