package com.seuapp.gateway.infra.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seuapp.gateway.domain.external.ExternalRequest;
import com.seuapp.gateway.domain.external.ExternalResponse;
import com.seuapp.gateway.domain.port.ExternalSystemClientBlocking;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@Profile("poc-loom")
public class HttpExternalSystemClientBlocking implements ExternalSystemClientBlocking {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI endpoint;
    private final ExecutorService httpExec =
            Executors.newFixedThreadPool(Math.max(4, Runtime.getRuntime().availableProcessors()));

    public HttpExternalSystemClientBlocking(
            @Value("${external.base-url}") String baseUrl,
            ObjectMapper objectMapper
    ) {
        this.objectMapper = objectMapper;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .executor(httpExec)
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        this.endpoint = URI.create(baseUrl + "/external/process");
    }

    private final Semaphore bulkhead = new Semaphore(500); // ajuste (300–1500)

    @Override
    public ExternalResponse call(ExternalRequest request, Duration timeout) throws Exception {
        if (!bulkhead.tryAcquire(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            throw new TimeoutException("bulkhead timeout");
        }
        final String json = toJson(request);
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(endpoint)
                    .timeout(timeout) // <-- timeout dinâmico por request
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            int status = response.statusCode();
            String body = response.body();

            if (status < 200 || status >= 300) {
                throw new RuntimeException("External call failed. status=" + status + " body=" + safe(body));
            }
            return fromJson(body);
        } finally {
            bulkhead.release();
        }

    }

    private String toJson(ExternalRequest req) throws IOException {
        return objectMapper.writeValueAsString(req);
    }

    private ExternalResponse fromJson(String json) throws IOException {
        return objectMapper.readValue(json, ExternalResponse.class);
    }

    private String safe(String s) {
        return (s == null) ? "" : s;
    }

    @Bean(destroyMethod = "shutdown")
    public ExecutorService httpClientExecutor() {
        return Executors.newFixedThreadPool(Math.max(4, Runtime.getRuntime().availableProcessors()));
    }

}
