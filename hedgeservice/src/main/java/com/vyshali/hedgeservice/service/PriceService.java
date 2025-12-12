package com.vyshali.hedgeservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceService {
    
    private final RestTemplate restTemplate = new RestTemplate();
    
    @Value("${fx-analyzer.price-service.base-url}")
    private String priceServiceBaseUrl;
    
    /**
     * Get FX rate for a currency pair.
     */
    @Cacheable(value = "fx-rates", key = "#currency + '_' + #asOfDate")
    public BigDecimal getFxRate(String currency, LocalDate asOfDate) {
        return getFxRate(currency, "USD", asOfDate);
    }
    
    /**
     * Get FX rate between two currencies.
     */
    @Cacheable(value = "fx-rates", key = "#fromCurrency + '_' + #toCurrency + '_' + #asOfDate")
    public BigDecimal getFxRate(String fromCurrency, String toCurrency, LocalDate asOfDate) {
        if (fromCurrency.equals(toCurrency)) {
            return BigDecimal.ONE;
        }
        
        try {
            // Call Price Service API to get FX rate
            String url = String.format("%s/api/v1/prices/fx-rate?from=%s&to=%s&date=%s",
                priceServiceBaseUrl, fromCurrency, toCurrency, asOfDate);
            
            FxRateResponse response = restTemplate.getForObject(url, FxRateResponse.class);
            
            if (response != null && response.rate() != null) {
                return response.rate();
            }
        } catch (Exception e) {
            log.error("Error fetching FX rate from Price Service: {} -> {} on {}", 
                fromCurrency, toCurrency, asOfDate, e);
        }
        
        // Fallback to hardcoded rates (for development/testing)
        return getHardcodedFxRate(fromCurrency, toCurrency);
    }
    
    /**
     * Hardcoded FX rates for development (fallback).
     */
    private BigDecimal getHardcodedFxRate(String fromCurrency, String toCurrency) {
        // Simplified exchange rates (for testing purposes)
        return switch (fromCurrency + toCurrency) {
            case "EURUSD" -> new BigDecimal("1.0850");
            case "USDEUR" -> new BigDecimal("0.9217");
            case "GBPUSD" -> new BigDecimal("1.2650");
            case "USDGBP" -> new BigDecimal("0.7905");
            case "JPYUSD" -> new BigDecimal("0.0068");
            case "USDJPY" -> new BigDecimal("148.50");
            case "CHFUSD" -> new BigDecimal("1.1250");
            case "USDCHF" -> new BigDecimal("0.8889");
            case "AUDUSD" -> new BigDecimal("0.6580");
            case "USDAUD" -> new BigDecimal("1.5198");
            default -> BigDecimal.ONE; // Default to 1:1 if unknown
        };
    }
    
    /**
     * Response DTO for FX rate.
     */
    public record FxRateResponse(
        String fromCurrency,
        String toCurrency,
        BigDecimal rate,
        LocalDate asOfDate,
        String source
    ) {}
}
