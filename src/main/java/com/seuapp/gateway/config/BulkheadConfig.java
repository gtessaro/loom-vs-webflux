package com.seuapp.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Semaphore;

/**
 * Bulkhead (limite de concorrência outbound).
 *
 * Por que existe?
 * - Loom (e também WebFlux) conseguem disparar muitas chamadas externas em paralelo.
 * - Em ambiente local, isso afoga o serviço externo (backlog TCP, fila do Tomcat/Netty) e vira timeout/fallback artificial.
 *
 * Como escolher o número?
 * - Regra prática (Little's Law): concorrência ≈ RPS * latência_média (segundos)
 * - Ex.: 250 RPS * 0.25s ≈ 62 concorrentes. Use 2x como folga => ~120.
 */
@Configuration
public class BulkheadConfig {

    @Bean
    public Semaphore outboundBulkhead() {
        return new Semaphore(500);
    }
}
