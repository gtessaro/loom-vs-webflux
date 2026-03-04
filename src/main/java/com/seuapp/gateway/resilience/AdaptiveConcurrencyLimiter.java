package com.seuapp.gateway.resilience;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Adaptive Concurrency Limiter (ACL) - "semaphore inteligente".
 *
 * Ideia:
 * - Mantém um limite dinâmico de chamadas externas simultâneas (limit).
 * - Observa a latência (RTT) das chamadas externas.
 * - Se a latência piora (congestionamento), reduz o limite.
 * - Se a latência melhora/estabiliza, aumenta devagar.
 *
 * Modelo (inspirado no Netflix gradient):
 * - usa minRTT (melhor RTT observado) como baseline
 * - calcula "gradient" = minRTT / currentRTT  (0..1)
 * - novo_limite ~ limite_atual * gradient  (reduz quando RTT sobe)
 *
 * Para evitar oscilação:
 * - currentRTT usa EWMA (média móvel exponencial)
 * - aumento é lento (step up), redução é rápida (multiplicativa)
 */
public final class AdaptiveConcurrencyLimiter {

    private final AtomicInteger inFlight = new AtomicInteger(0);

    // limite atual (mutável)
    private volatile int limit;

    // limites de segurança
    private final int minLimit;
    private final int maxLimit;

    // EWMA para RTT (ms) e minRTT observado (ms)
    private final AtomicLong ewmaRttMicros = new AtomicLong(0);
    private volatile long minRttMicros = Long.MAX_VALUE;

    // parâmetros de ajuste
    private final double ewmaAlpha;     // 0.05..0.2 (menor = mais suave)
    private final int stepUp;           // quanto sobe quando está saudável
    private final double backoff;       // redução multiplicativa extra (ex: 0.90)
    private final long warmupRequests;  // não reduzir agressivo no começo
    private final AtomicLong samples = new AtomicLong(0);

    public AdaptiveConcurrencyLimiter(int initialLimit,
                                      int minLimit,
                                      int maxLimit,
                                      double ewmaAlpha,
                                      int stepUp,
                                      double backoff,
                                      long warmupRequests) {
        this.limit = initialLimit;
        this.minLimit = minLimit;
        this.maxLimit = maxLimit;
        this.ewmaAlpha = ewmaAlpha;
        this.stepUp = stepUp;
        this.backoff = backoff;
        this.warmupRequests = warmupRequests;
    }

    /**
     * Tenta reservar um slot.
     * Se ultrapassar o limite atual, devolve false (shed load / fallback imediato).
     */
    public boolean tryAcquire() {
        int now = inFlight.incrementAndGet();
        if (now <= limit) return true;

        // passou do limite -> desfaz e recusa
        inFlight.decrementAndGet();
        return false;
    }

    /** Libera o slot (sempre chame em finally). */
    public void release() {
        inFlight.decrementAndGet();
    }

    /**
     * Reporta a latência da chamada externa (RTT).
     * Use isto somente quando você REALMENTE chamou o externo (não em fallback imediato).
     */
    public void onSample(long rttMicros) {
        if (rttMicros <= 0) return;
        samples.incrementAndGet();

        // atualiza minRTT (baseline)
        if (rttMicros < minRttMicros) {
            minRttMicros = rttMicros;
        }

        // EWMA: rtt = alpha*new + (1-alpha)*old
        long prev = ewmaRttMicros.get();
        if (prev == 0) {
            ewmaRttMicros.set(rttMicros);
        } else {
            long next = (long) (ewmaAlpha * rttMicros + (1.0 - ewmaAlpha) * prev);
            ewmaRttMicros.set(next);
        }

        // evita decisões ruins no warmup (no começo minRTT ainda não estabilizou)
        if (samples.get() < warmupRequests) {
            return;
        }

        long rtt = ewmaRttMicros.get();
        long min = minRttMicros;

        // gradient (0..1): quando RTT sobe, gradient cai
        double gradient = (double) min / (double) rtt;
        if (gradient > 1.0) gradient = 1.0;

        int current = this.limit;

        // Se RTT piorou: reduz mais rápido (multiplicativo)
        // Se RTT está ok: aumenta devagar (step up)
        int newLimit;
        if (gradient < 0.95) {
            // redução orientada pelo gradient + backoff
            newLimit = (int) Math.floor(current * gradient * backoff);
        } else {
            // saudável -> sobe pouco
            newLimit = current + stepUp;
        }

        // clamp
        if (newLimit < minLimit) newLimit = minLimit;
        if (newLimit > maxLimit) newLimit = maxLimit;

        this.limit = newLimit;
    }

    // úteis para debug via logs/actuator se quiser
    public int getLimit() { return limit; }
    public int getInFlight() { return inFlight.get(); }
    public long getMinRttMicros() { return minRttMicros == Long.MAX_VALUE ? 0 : minRttMicros; }
    public long getEwmaRttMicros() { return ewmaRttMicros.get(); }
}
