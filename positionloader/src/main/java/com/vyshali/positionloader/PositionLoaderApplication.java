package com.vyshali.positionloader;

import com.vyshali.positionloader.config.AppConfig.LoaderConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Position Loader Application
 *
 * Phase 1 Enhancement #5: Added @EnableConfigurationProperties for LoaderConfig
 */
@SpringBootApplication
@EnableConfigurationProperties(LoaderConfig.class)
public class PositionLoaderApplication {
    public static void main(String[] args) {
        SpringApplication.run(PositionLoaderApplication.class, args);
    }
}