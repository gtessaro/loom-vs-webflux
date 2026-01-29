package com.seuapp.domain.policy;

import com.seuapp.config.PolicyProperties;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Compila ranges em um snapshot e troca em hot-reload quando o Environment muda (Consul/refresh).
 */
@Component
public class RangePolicyEngine {

    private final PolicyProperties props;
    private final AtomicReference<PolicySnapshot> snapshotRef = new AtomicReference<>();

    public RangePolicyEngine(PolicyProperties props) {
        this.props = props;
        this.snapshotRef.set(PolicySnapshot.compile(props.timeoutByRange(), props.fallbackByRange()));
    }

    public long resolveTimeoutMs(long value) {
        return snapshotRef.get().resolveTimeoutMs(value);
    }

    public String resolveFallbackResult(long value) {
        return snapshotRef.get().resolveFallback(value);
    }

    @EventListener(EnvironmentChangeEvent.class)
    public void onEnvChange(EnvironmentChangeEvent evt) {
        boolean relevant = evt.getKeys().stream().anyMatch(k -> k.startsWith("policy."));
        if (!relevant) return;
        snapshotRef.set(PolicySnapshot.compile(props.timeoutByRange(), props.fallbackByRange()));
    }
}
