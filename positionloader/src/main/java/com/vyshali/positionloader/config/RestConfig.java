package com.vyshali.positionloader.config;

/*
 * 12/1/25 - 22:55
 * @author Vyshali Prabananth Lal
 */

/*
 * CRITICAL FIX #4: Timeout Configuration
 *
 * Problem: RestClient has NO TIMEOUTS by default
 * Issue #3: "SOAP... positions don't get loaded for accounts due to some error"
 *
 * If MSPM hangs, your thread hangs FOREVER without this fix.
 *
 * @author Vyshali Prabananth Lal
 */

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class RestConfig {

    @Value("${upstream.mspm.base-url:http://mspm-service}")
    private String mspmBaseUrl;

    @Value("${upstream.mspm.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${upstream.mspm.read-timeout-ms:30000}")
    private int readTimeoutMs;

    /**
     * MSPM REST Client with proper timeouts
     * <p>
     * - Connect timeout: 5 seconds (fail fast if service is down)
     * - Read timeout: 30 seconds (MSPM can be slow for large accounts)
     */
    @Bean
    public RestClient mspmClient() {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(connectTimeoutMs)).build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        return RestClient.builder().baseUrl(mspmBaseUrl).requestFactory(requestFactory).defaultHeader("Accept", "application/json").defaultHeader("Content-Type", "application/json").build();
    }

    /**
     * Generic REST client for other services
     */
    @Bean
    public RestClient genericRestClient() {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(30));

        return RestClient.builder().requestFactory(requestFactory).build();
    }
}