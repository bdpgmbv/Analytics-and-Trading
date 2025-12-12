package com.vyshali.positionloader;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Position Loader Service
 * 
 * Consumes position data from MSPM via Kafka and persists to database.
 * Creates snapshots (EOD/INTRADAY) and calculates currency exposures.
 * 
 * Port: 8081
 */
@SpringBootApplication(scanBasePackages = {
    "com.vyshali.fxanalyzer.positionloader",
    "com.vyshali.fxanalyzer.common"
})
@EntityScan(basePackages = "com.vyshali.fxanalyzer.common.entity")
@EnableJpaRepositories(basePackages = "com.vyshali.fxanalyzer.common.repository")
@EnableScheduling
public class PositionLoaderApplication {

    public static void main(String[] args) {
        SpringApplication.run(PositionLoaderApplication.class, args);
    }
}
