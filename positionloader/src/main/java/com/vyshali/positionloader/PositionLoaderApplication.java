package com.vyshali.positionloader;

import com.vyshali.positionloader.config.LoaderProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Position Loader Application
 * 
 * A Spring Boot service for loading and managing trading positions.
 * Supports EOD batch processing, intraday updates, and position reconciliation.
 */
@SpringBootApplication
@EnableConfigurationProperties(LoaderProperties.class)
@EnableScheduling
@EnableAsync
public class PositionLoaderApplication {
    public static void main(String[] args) {
        SpringApplication.run(PositionLoaderApplication.class, args);
    }
}
