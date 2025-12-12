package com.vyshali.priceservice.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Scheduled job to detect and mark stale prices.
 * 
 * Runs every minute to check for prices that haven't been updated
 * within the staleness threshold.
 */
@Slf4j
@Component
public class PriceStalenessChecker {

    private final PriceService priceService;
    
    @Value("${fxanalyzer.price.staleness-threshold-minutes:30}")
    private int stalenessThresholdMinutes;
    
    private final AtomicInteger stalePriceCount = new AtomicInteger(0);

    public PriceStalenessChecker(PriceService priceService, MeterRegistry meterRegistry) {
        this.priceService = priceService;
        
        // Register gauge for stale price count
        Gauge.builder("price.stale.count", stalePriceCount, AtomicInteger::get)
                .description("Current count of stale prices")
                .register(meterRegistry);
    }

    /**
     * Check for stale prices every minute.
     */
    @Scheduled(fixedRateString = "${fxanalyzer.price.staleness-check-interval-ms:60000}")
    public void checkForStalePrices() {
        log.debug("Running price staleness check");
        
        try {
            LocalDate cutoffDate = LocalDate.now().minusDays(1);
            int markedStale = priceService.markPricesAsStale(cutoffDate);
            
            if (markedStale > 0) {
                log.warn("Marked {} prices as stale", markedStale);
                stalePriceCount.set(markedStale);
            }
            
            // Update gauge with current stale count
            int currentStaleCount = priceService.getStalePrices().size();
            stalePriceCount.set(currentStaleCount);
            
        } catch (Exception e) {
            log.error("Error during staleness check: {}", e.getMessage(), e);
        }
    }
}
