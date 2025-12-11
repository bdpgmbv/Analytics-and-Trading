package com.vyshali.positionloader.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * REST client configuration.
 */
@Configuration
public class RestClientConfig {
    
    private final LoaderProperties properties;
    
    public RestClientConfig(LoaderProperties properties) {
        this.properties = properties;
    }
    
    @Bean
    public RestClient.Builder restClientBuilder() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        Duration timeout = properties.mspm().timeout();
        factory.setConnectTimeout((int) timeout.toMillis());
        factory.setReadTimeout((int) timeout.toMillis());
        
        return RestClient.builder()
            .requestFactory(factory);
    }
    
    @Bean
    public RestClient restClient(RestClient.Builder builder) {
        return builder.build();
    }
}
