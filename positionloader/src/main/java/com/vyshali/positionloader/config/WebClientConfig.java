package com.vyshali.positionloader.config;

/*
 * SIMPLIFIED: WebClientConfig with conditional logging
 *
 * BEFORE: Logging filters always active (overhead in production)
 * AFTER:  Logging only in dev/debug mode
 *
 * WHY:
 * - Request/response logging adds latency
 * - Can log sensitive data in production
 * - Not needed for normal operations
 */

import io.netty.channel.ChannelOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Slf4j
@Configuration
public class WebClientConfig {

    @Value("${mspm.base-url:http://localhost:8080}")
    private String mspmBaseUrl;

    @Value("${mspm.timeout:30s}")
    private Duration timeout;

    @Value("${mspm.connect-timeout:10s}")
    private Duration connectTimeout;

    // ═══════════════════════════════════════════════════════════════════════════
    // CONDITIONAL LOGGING - Only enable in dev/debug
    // ═══════════════════════════════════════════════════════════════════════════
    @Value("${webclient.logging.enabled:false}")
    private boolean loggingEnabled;

    @Bean
    public WebClient mspmWebClient(WebClient.Builder builder) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) connectTimeout.toMillis())
                .responseTimeout(timeout);

        WebClient.Builder webClientBuilder = builder
                .baseUrl(mspmBaseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient));

        // ───────────────────────────────────────────────────────────────────────
        // CONDITIONAL: Only add logging filters if enabled
        // ───────────────────────────────────────────────────────────────────────
        if (loggingEnabled) {
            log.info("WebClient request/response logging ENABLED");
            webClientBuilder
                    .filter(logRequest())
                    .filter(logResponse());
        }

        return webClientBuilder.build();
    }

    /**
     * Log outgoing requests (only when logging enabled).
     */
    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            log.debug(">>> {} {}", request.method(), request.url());
            request.headers().forEach((name, values) -> {
                // Don't log sensitive headers
                if (!name.equalsIgnoreCase("Authorization")) {
                    values.forEach(value -> log.debug(">>> {}: {}", name, value));
                }
            });
            return Mono.just(request);
        });
    }

    /**
     * Log incoming responses (only when logging enabled).
     */
    private ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(response -> {
            log.debug("<<< {} {}", response.statusCode().value(), response.statusCode());
            return Mono.just(response);
        });
    }
}