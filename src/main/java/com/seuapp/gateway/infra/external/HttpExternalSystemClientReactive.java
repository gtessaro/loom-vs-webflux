package com.seuapp.gateway.infra.external;

import com.seuapp.gateway.domain.external.ExternalRequest;
import com.seuapp.gateway.domain.external.ExternalResponse;
import com.seuapp.gateway.domain.port.ExternalSystemClientReactive;
import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Component
@Profile("poc-webflux")
public class HttpExternalSystemClientReactive implements ExternalSystemClientReactive {

    private final WebClient webClient;

    public HttpExternalSystemClientReactive(@Value("${external.base-url}") String baseUrl) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 200);
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    @Override
    public Mono<ExternalResponse> call(ExternalRequest request, Duration timeout) {
        return webClient.post()
                .uri("/external/process")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ExternalResponse.class)
                .timeout(timeout);
    }
}
