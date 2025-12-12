package com.vyshali.tradefillprocessor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Trade Fill Processor Service
 * 
 * Consumes FX trade fills from FXMatrix via Kafka and maintains
 * full execution audit trail (Issue #2 solution).
 * 
 * Flow: Hedge Service sends trade → FXMatrix → Fill back → This service records
 * 
 * Tracks: SENT → ACKNOWLEDGED → EXECUTED/REJECTED/FAILED/CANCELLED
 * 
 * Port: 8083
 */
@SpringBootApplication(scanBasePackages = {
    "com.vyshali.fxanalyzer.tradefillprocessor",
    "com.vyshali.fxanalyzer.common"
})
@EntityScan(basePackages = "com.vyshali.fxanalyzer.common.entity")
@EnableJpaRepositories(basePackages = "com.vyshali.fxanalyzer.common.repository")
@EnableScheduling
public class TradeFillProcessorApplication {

    public static void main(String[] args) {
        SpringApplication.run(TradeFillProcessorApplication.class, args);
    }
}
