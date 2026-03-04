package com.seuapp.gateway.domain.port;

import com.seuapp.gateway.domain.service.OperationLogEvent;

public interface LogPublisher {
    void publish(OperationLogEvent event);
}
