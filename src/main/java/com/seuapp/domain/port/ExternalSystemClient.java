package com.seuapp.domain.port;

import com.seuapp.domain.external.ExternalRequest;
import com.seuapp.domain.external.ExternalResponse;

public interface ExternalSystemClient {
    ExternalResponse call(ExternalRequest request) throws Exception;
}
