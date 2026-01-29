package com.seuapp.infra.external;

import com.seuapp.config.ExternalSimProperties;
import com.seuapp.domain.external.ExternalRequest;
import com.seuapp.domain.external.ExternalResponse;
import com.seuapp.domain.port.ExternalSystemClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component
public class ExternalSystemMockClient implements ExternalSystemClient {

    private final ExternalSimProperties sim;

    public ExternalSystemMockClient(ExternalSimProperties sim) {
        this.sim = sim;
    }

    @Override
    public ExternalResponse call(ExternalRequest request) throws Exception {
        long v = Long.parseLong(request.externalValue());

        if (v >= sim.errorAfterValue()) {
            throw new RuntimeException("Simulated external error for value=" + v);
        }

        long jitter = ThreadLocalRandom.current().nextLong(0, Math.max(sim.jitterMs(), 1));
        long latency = sim.baseLatencyMs() + jitter;

        if (v >= sim.timeoutAfterValue()) {
            latency += 1500;
        }

        Thread.sleep(latency);

        return new ExternalResponse("200", "MOCK_OK(value=" + v + ")");
    }
}
