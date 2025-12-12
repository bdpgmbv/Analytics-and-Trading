package com.vyshali.priceservice.controller;

import com.vyshali.fxanalyzer.common.dto.ApiResponse;
import com.vyshali.fxanalyzer.common.dto.FxRateDto;
import com.vyshali.fxanalyzer.common.dto.PriceDto;
import com.vyshali.fxanalyzer.priceservice.cache.PriceCacheService;
import com.vyshali.fxanalyzer.priceservice.service.FxRateService;
import com.vyshali.fxanalyzer.priceservice.service.PriceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for Price Service.
 * Provides security prices and FX rates with caching.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/prices")
@RequiredArgsConstructor
@Tag(name = "Price Service", description = "Security prices and FX rates")
public class PriceController {

    private final PriceService priceService;
    private final FxRateService fxRateService;
    private final PriceCacheService cacheService;

    // ==================== Health & Status ====================

    @GetMapping("/health")
    @Operation(summary = "Health check")
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "price-service");
        health.put("cacheStats", cacheService.getStats());
        return ResponseEntity.ok(ApiResponse.success(health));
    }

    // ==================== Security Prices ====================

    @GetMapping("/{productId}")
    @Operation(summary = "Get price by product ID")
    public ResponseEntity<ApiResponse<PriceDto>> getPrice(@PathVariable Long productId) {
        PriceDto price = priceService.getPrice(productId);
        return ResponseEntity.ok(ApiResponse.success(price));
    }

    @GetMapping("/lookup")
    @Operation(summary = "Get price by identifier (CUSIP, ISIN, etc.)")
    public ResponseEntity<ApiResponse<PriceDto>> getPriceByIdentifier(
            @RequestParam String identifierType,
            @RequestParam String identifier) {
        PriceDto price = priceService.getPriceByIdentifier(identifierType, identifier);
        return ResponseEntity.ok(ApiResponse.success(price));
    }

    @PostMapping("/batch")
    @Operation(summary = "Get prices for multiple products")
    public ResponseEntity<ApiResponse<List<PriceDto>>> getPrices(@RequestBody List<Long> productIds) {
        List<PriceDto> prices = priceService.getPrices(productIds);
        return ResponseEntity.ok(ApiResponse.success(prices));
    }

    @GetMapping("/stale")
    @Operation(summary = "Get all stale prices")
    public ResponseEntity<ApiResponse<List<PriceDto>>> getStalePrices() {
        List<PriceDto> stalePrices = priceService.getStalePrices();
        return ResponseEntity.ok(ApiResponse.success(stalePrices));
    }

    @PostMapping("/{productId}")
    @Operation(summary = "Update price (for testing/manual override)")
    public ResponseEntity<ApiResponse<PriceDto>> updatePrice(
            @PathVariable Long productId,
            @RequestParam BigDecimal price,
            @RequestParam(defaultValue = "MANUAL") String source,
            @RequestParam(defaultValue = "1") Integer priority) {
        PriceDto updated = priceService.updatePrice(productId, price, source, priority);
        return ResponseEntity.ok(ApiResponse.success(updated, "Price updated"));
    }

    // ==================== FX Rates ====================

    @GetMapping("/fx/{currencyPair}")
    @Operation(summary = "Get FX rate for currency pair")
    public ResponseEntity<ApiResponse<FxRateDto>> getFxRate(@PathVariable String currencyPair) {
        FxRateDto rate = fxRateService.getFxRate(currencyPair);
        return ResponseEntity.ok(ApiResponse.success(rate));
    }

    @GetMapping("/fx")
    @Operation(summary = "Get all FX rates")
    public ResponseEntity<ApiResponse<List<FxRateDto>>> getAllFxRates() {
        List<FxRateDto> rates = fxRateService.getAllRates();
        return ResponseEntity.ok(ApiResponse.success(rates));
    }

    @PostMapping("/fx/batch")
    @Operation(summary = "Get FX rates for multiple currency pairs")
    public ResponseEntity<ApiResponse<List<FxRateDto>>> getFxRates(@RequestBody List<String> currencyPairs) {
        List<FxRateDto> rates = fxRateService.getRates(currencyPairs);
        return ResponseEntity.ok(ApiResponse.success(rates));
    }

    @GetMapping("/fx/convert")
    @Operation(summary = "Get conversion rate between currencies")
    public ResponseEntity<ApiResponse<BigDecimal>> getConversionRate(
            @RequestParam String from,
            @RequestParam String to) {
        BigDecimal rate = fxRateService.getConversionRate(from, to);
        return ResponseEntity.ok(ApiResponse.success(rate));
    }

    @PostMapping("/fx/{currencyPair}")
    @Operation(summary = "Update FX rate (for testing/manual override)")
    public ResponseEntity<ApiResponse<FxRateDto>> updateFxRate(
            @PathVariable String currencyPair,
            @RequestParam BigDecimal midRate,
            @RequestParam(defaultValue = "MANUAL") String source) {
        FxRateDto updated = fxRateService.updateFxRate(currencyPair, midRate, source);
        return ResponseEntity.ok(ApiResponse.success(updated, "FX rate updated"));
    }

    // ==================== Cache Management ====================

    @GetMapping("/cache/stats")
    @Operation(summary = "Get cache statistics")
    public ResponseEntity<ApiResponse<PriceCacheService.CacheStats>> getCacheStats() {
        return ResponseEntity.ok(ApiResponse.success(cacheService.getStats()));
    }

    @DeleteMapping("/cache/price/{productId}")
    @Operation(summary = "Evict price from cache")
    public ResponseEntity<ApiResponse<Void>> evictPriceCache(@PathVariable Long productId) {
        cacheService.evictPrice(productId);
        return ResponseEntity.ok(ApiResponse.success(null, "Price cache evicted"));
    }

    @DeleteMapping("/cache/fx/{currencyPair}")
    @Operation(summary = "Evict FX rate from cache")
    public ResponseEntity<ApiResponse<Void>> evictFxRateCache(@PathVariable String currencyPair) {
        cacheService.evictFxRate(currencyPair);
        return ResponseEntity.ok(ApiResponse.success(null, "FX rate cache evicted"));
    }
}
