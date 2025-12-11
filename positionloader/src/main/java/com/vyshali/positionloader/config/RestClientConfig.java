package com.vyshali.positionloader.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * REST client configuration for external service calls.
 */
@Configuration
public class RestClientConfig {
    
    private final LoaderProperties loaderProperties;
    
    public RestClientConfig(LoaderProperties loaderProperties) {
        this.loaderProperties = loaderProperties;
    }
    
    /**
     * MSPM REST client for fetching positions.
     */
    @Bean
    public RestClient mspmRestClient() {
        Duration timeout = loaderProperties.mspm().timeout();
        
        return RestClient.builder()
            .baseUrl(loaderProperties.mspm().baseUrl())
            .requestFactory(createRequestFactory(timeout))
            .defaultHeader("Content-Type", "application/json")
            .defaultHeader("Accept", "application/json")
            .build();
    }
    
    /**
     * General purpose REST template for other integrations.
     */
    @Bean
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setRequestFactory(createRequestFactory(Duration.ofSeconds(30)));
        return restTemplate;
    }
    
    private SimpleClientHttpRequestFactory createRequestFactory(Duration timeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return factory;
    }
}
