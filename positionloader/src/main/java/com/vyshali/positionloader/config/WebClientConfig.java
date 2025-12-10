package com.vyshali.positionloader.config;

/*
 * 12/10/2025 - NEW: WebClient configuration for async REST notifications
 *
 * @author Vyshali Prabananth Lal
 */

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
public class WebClientConfig {

    @Value("${services.notification.connect-timeout-ms:2000}")
    private int connectTimeoutMs;

    @Value("${services.notification.read-timeout-ms:2000}")
    private int readTimeoutMs;

    @Value("${services.notification.write-timeout-ms:2000}")
    private int writeTimeoutMs;

    /**
     * WebClient builder with timeout configuration.
     * Used for async REST notifications to Price and Hedge services.
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        HttpClient httpClient = HttpClient.create().option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs).responseTimeout(Duration.ofMillis(readTimeoutMs)).doOnConnected(conn -> conn.addHandlerLast(new ReadTimeoutHandler(readTimeoutMs, TimeUnit.MILLISECONDS)).addHandlerLast(new WriteTimeoutHandler(writeTimeoutMs, TimeUnit.MILLISECONDS)));

        return WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient)).filter(logRequest()).filter(logResponse());
    }

    /**
     * Log outgoing requests (debug level).
     */
    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            log.debug("Request: {} {}", clientRequest.method(), clientRequest.url());
            return Mono.just(clientRequest);
        });
    }

    /**
     * Log responses (debug level).
     */
    private ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            log.debug("Response: {} from {}", clientResponse.statusCode(), clientResponse.request().getURI());
            return Mono.just(clientResponse);
        });
    }
}