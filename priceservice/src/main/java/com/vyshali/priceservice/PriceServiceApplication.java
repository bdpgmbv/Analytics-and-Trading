package com.vyshali.priceservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Price Service
 * 
 * Provides security prices and FX rates with multi-source hierarchy.
 * Features: L1 Caffeine + L2 Redis caching, WebSocket real-time updates,
 * circuit breakers for upstream services, staleness detection.
 * 
 * Price Hierarchy: OVERRIDE (1) > REALTIME (2) > RCP_SNAP (3) > MSPA (4)
 * 
 * Port: 8082
 */
@SpringBootApplication(scanBasePackages = {
    "com.vyshali.fxanalyzer.priceservice",
    "com.vyshali.fxanalyzer.common"
})
@EntityScan(basePackages = "com.vyshali.fxanalyzer.common.entity")
@EnableJpaRepositories(basePackages = "com.vyshali.fxanalyzer.common.repository")
@EnableCaching
@EnableScheduling
public class PriceServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PriceServiceApplication.class, args);
    }
}
