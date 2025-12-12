package com.vyshali.positionloader;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Position Loader - Loads and manages account positions for FX Analyzer.
 * 
 * ✅ MODIFIED: Added scanBasePackages to include common module beans
 * 
 * Responsibilities:
 * - Load positions from custodians/prime brokers
 * - Reconcile positions against trade history
 * - Calculate real-time market values
 * - Publish position snapshots to Kafka
 */
@SpringBootApplication(scanBasePackages = {
    "com.vyshali.positionloader",
    "com.vyshali.common"  // ✅ ADD THIS - Include common module beans
})
@EnableScheduling
public class PositionLoaderApplication {
    public static void main(String[] args) {
        SpringApplication.run(PositionLoaderApplication.class, args);
    }
}
