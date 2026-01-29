package com.seuapp.infra.logging;

import com.seuapp.domain.port.LogPublisher;
import com.seuapp.domain.service.OperationLogEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Mock do envio para tópico (Kafka, etc.).
 * Para POC, só loga no console.
 */
@Component
public class TopicLogPublisherMock implements LogPublisher {

    private static final Logger log = LoggerFactory.getLogger(TopicLogPublisherMock.class);

    @Value("${logging.topic.enabled:true}")
    private boolean enabled;

    @Override
    public void publish(OperationLogEvent event) {
        if (!enabled) return;
        log.info("[TOPIC-MOCK] inbound.value={}, timeoutMs={}, fallback={}, external.status={}",
                event.inbound().value(),
                event.timeoutMsApplied(),
                event.fallbackApplied(),
                event.responseFromExternal() != null ? event.responseFromExternal().status() : "null"
        );
    }
}
