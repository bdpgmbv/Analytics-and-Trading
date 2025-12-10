package com.vyshali.positionloader.config;

/*
 * 12/10/2025 - 12:36 PM
 * @author Vyshali Prabananth Lal
 */

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

import javax.sql.DataSource;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Core application configuration.
 * Combines: REST clients, distributed locking, and common beans.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "10m")
public class AppConfig {

    @Value("${upstream.mspm.base-url:http://mspm-service}")
    private String mspmBaseUrl;

    @Value("${upstream.mspm.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${upstream.mspm.read-timeout-ms:30000}")
    private int readTimeoutMs;

    // ==================== REST CLIENTS ====================

    @Bean
    public RestClient mspmClient() {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(connectTimeoutMs)).build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        return RestClient.builder().baseUrl(mspmBaseUrl).requestFactory(factory).defaultHeader("Accept", "application/json").defaultHeader("Content-Type", "application/json").build();
    }

    // ==================== DISTRIBUTED LOCKING ====================

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder().withJdbcTemplate(new JdbcTemplate(dataSource)).usingDbTime().build());
    }
}