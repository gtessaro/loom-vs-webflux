package com.seuapp.gateway.domain.policy;

import com.seuapp.gateway.config.PolicyProperties;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

@Component
public class RangePolicyEngine {
    private final PolicyProperties props;
    private final AtomicReference<PolicySnapshot> snapshotRef = new AtomicReference<>();

    public RangePolicyEngine(PolicyProperties props){
        this.props = props;
        this.snapshotRef.set(PolicySnapshot.compile(props.timeoutByRange(), props.fallbackByRange()));
    }

    public long resolveTimeoutMs(long value){ return snapshotRef.get().resolveTimeoutMs(value); }
    public String resolveFallbackResult(long value){ return snapshotRef.get().resolveFallback(value); }

    @EventListener(EnvironmentChangeEvent.class)
    public void onEnvChange(EnvironmentChangeEvent evt){
        boolean relevant = evt.getKeys().stream().anyMatch(k -> k.startsWith("policy."));
        if(!relevant) return;
        snapshotRef.set(PolicySnapshot.compile(props.timeoutByRange(), props.fallbackByRange()));
    }
}
