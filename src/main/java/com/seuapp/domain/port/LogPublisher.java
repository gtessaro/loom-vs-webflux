package com.seuapp.domain.port;

import com.seuapp.domain.service.OperationLogEvent;

public interface LogPublisher {
    void publish(OperationLogEvent event);
}
