package com.seuapp.gateway.config;

import com.seuapp.gateway.resilience.AdaptiveConcurrencyLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Config do Adaptive Limiter para ambos perfis (loom e webflux).
 *
 * Valores iniciais bons para localhost:
 * - initial=120 (similar ao Semaphore do v8)
 * - min=20 (não deixa cair demais)
 * - max=400 (evita "tempestade")
 * - alpha=0.1 (suavização)
 * - stepUp=2 (sobe devagar)
 * - backoff=0.90 (cai um pouco mais quando piora)
 * - warmup=200 amostras
 */
@Configuration
public class AdaptiveLimiterConfig {

    @Bean
    public AdaptiveConcurrencyLimiter outboundLimiter() {
        return new AdaptiveConcurrencyLimiter(
                120,   // initial
                20,    // min
                400,   // max
                0.10,  // ewmaAlpha
                2,     // stepUp
                0.90,  // backoff
                200    // warmupRequests
        );
    }
}
