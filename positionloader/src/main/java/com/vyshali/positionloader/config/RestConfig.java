package com.vyshali.positionloader.config;

/*
 * 12/1/25 - 22:55
 * @author Vyshali Prabananth Lal
 */

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestConfig {
    @Bean
    public RestClient mspmClient(@Value("${upstream.mspm.base-url}") String baseUrl) {
        return RestClient.builder().baseUrl(baseUrl).defaultHeader("App-ID", "FXAN-EOD").build();
    }
}