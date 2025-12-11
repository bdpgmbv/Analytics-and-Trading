package com.vyshali.priceservice.controller;

/*
 * 12/11/2025 - REST Controller for Price Service
 * @author Vyshali Prabananth Lal
 *
 * Provides HTTP endpoints for price and FX rate queries.
 * Complements WebSocket real-time streaming with request/response API.
 */

import com.vyshali.priceservice.dto.FxRateDTO;
import com.vyshali.priceservice.dto.PriceTickDTO;
import com.vyshali.priceservice.repository.FxRepository;
import com.vyshali.priceservice.service.FxCacheService;
import com.vyshali.priceservice.service.PriceCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PriceController {

    private final PriceCacheService priceCache;
    private final FxCacheService fxCache;
    private final FxRepository fxRepository;

    // ============================================================
    // Price Endpoints
    // ============================================================

    /**
     * Get latest price for a single product.
     * GET /api/prices/{productId}
     */
    @GetMapping("/prices/{productId}")
    public ResponseEntity<PriceTickDTO> getPrice(@PathVariable Integer productId) {
        PriceTickDTO price = priceCache.getPrice(productId);
        if (price == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(price);
    }

    /**
     * Get latest prices for multiple products.
     * POST /api/prices/batch
     * Body: [1001, 1002, 1003]
     */
    @PostMapping("/prices/batch")
    public ResponseEntity<Map<Integer, PriceTickDTO>> getPrices(@RequestBody List<Integer> productIds) {
        Map<Integer, PriceTickDTO> prices = new java.util.HashMap<>();
        for (Integer id : productIds) {
            PriceTickDTO price = priceCache.getPrice(id);
            if (price != null) {
                prices.put(id, price);
            }
        }
        return ResponseEntity.ok(prices);
    }

    // ============================================================
    // FX Rate Endpoints
    // ============================================================

    /**
     * Get FX conversion rate between two currencies.
     * GET /api/fx/rate?from=EUR&to=USD
     */
    @GetMapping("/fx/rate")
    public ResponseEntity<Map<String, Object>> getFxRate(
            @RequestParam String from,
            @RequestParam String to) {

        BigDecimal rate = fxCache.getConversionRate(from, to);

        return ResponseEntity.ok(Map.of(
                "from", from,
                "to", to,
                "rate", rate,
                "pair", from + "/" + to
        ));
    }

    /**
     * Get all latest FX rates.
     * GET /api/fx/rates
     */
    @GetMapping("/fx/rates")
    public ResponseEntity<List<FxRateDTO>> getAllFxRates() {
        return ResponseEntity.ok(fxRepository.getAllLatestRates());
    }

    /**
     * Convert amount between currencies.
     * GET /api/fx/convert?from=EUR&to=USD&amount=1000
     */
    @GetMapping("/fx/convert")
    public ResponseEntity<Map<String, Object>> convertCurrency(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam BigDecimal amount) {

        BigDecimal rate = fxCache.getConversionRate(from, to);
        BigDecimal converted = amount.multiply(rate);

        return ResponseEntity.ok(Map.of(
                "from", from,
                "to", to,
                "originalAmount", amount,
                "convertedAmount", converted,
                "rate", rate
        ));
    }

    // ============================================================
    // Health/Status Endpoints
    // ============================================================

    /**
     * Get price service status and cache statistics.
     * GET /api/prices/status
     */
    @GetMapping("/prices/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(Map.of(
                "service", "priceservice",
                "status", "UP",
                "timestamp", java.time.Instant.now()
        ));
    }
}
