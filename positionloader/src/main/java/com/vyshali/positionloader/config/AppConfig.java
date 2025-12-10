package com.vyshali.positionloader.config;

/*
 * 12/10/2025 - 12:36 PM
 * @author Vyshali Prabananth Lal
 */

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Application configuration.
 */
@Configuration
public class AppConfig {

    @Value("${upstream.mspm.base-url:http://localhost:8081/mspm}")
    private String mspmBaseUrl;

    @Value("${upstream.mspm.timeout-seconds:30}")
    private int timeoutSeconds;

    @Bean
    public RestClient mspmClient() {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(timeoutSeconds)).build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));

        return RestClient.builder().baseUrl(mspmBaseUrl).requestFactory(factory).defaultHeader("Accept", "application/json").build();
    }
}