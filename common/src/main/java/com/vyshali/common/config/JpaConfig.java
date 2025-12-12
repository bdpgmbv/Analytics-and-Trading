package com.vyshali.common.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * JPA configuration for FX Analyzer
 */
@Configuration
@EnableJpaAuditing
@EnableTransactionManagement
@EntityScan(basePackages = "com.vyshali.fxanalyzer.common.entity")
@EnableJpaRepositories(basePackages = "com.vyshali.fxanalyzer.common.repository")
public class JpaConfig {
    // JPA configuration is handled through annotations and application.yml
}
