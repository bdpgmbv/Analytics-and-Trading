package com.vyshali.common.service;

import com.vyshali.common.dto.SharedDto.FxRateDTO;
import com.vyshali.common.repository.FxRateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for FX rate conversions.
 * Supports direct rates, inverse rates, and cross rates.
 */
@Service
public class FxConversionService {

    private static final Logger log = LoggerFactory.getLogger(FxConversionService.class);
    private static final int SCALE = 8;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private final FxRateRepository fxRateRepository;

    @Value("${app.fx.base-currency:USD}")
    private String baseCurrency;

    // In-memory cache for real-time rates (populated by Price Service)
    private final Map<String, FxRateDTO> realtimeRates = new ConcurrentHashMap<>();

    public FxConversionService(FxRateRepository fxRateRepository) {
        this.fxRateRepository = fxRateRepository;
    }

    /**
     * Get conversion rate from one currency to another.
     * Supports direct rates, inverse rates, and cross rates.
     */
    public BigDecimal getRate(String fromCcy, String toCcy, LocalDate date) {
        // Null safety
        if (fromCcy == null || toCcy == null) {
            log.warn("Null currency in conversion: from={}, to={}", fromCcy, toCcy);
            return BigDecimal.ONE;
        }

        String from = fromCcy.toUpperCase();
        String to = toCcy.toUpperCase();

        // Same currency = 1:1
        if (from.equals(to)) {
            return BigDecimal.ONE;
        }

        // Try direct rate: FROM/TO (e.g., EUR/USD)
        Optional<BigDecimal> direct = getDirectRate(from + to, date);
        if (direct.isPresent()) {
            return direct.get();
        }

        // Try inverse rate: TO/FROM (e.g., USD/EUR)
        Optional<BigDecimal> inverse = getDirectRate(to + from, date);
        if (inverse.isPresent() && inverse.get().compareTo(BigDecimal.ZERO) != 0) {
            return BigDecimal.ONE.divide(inverse.get(), SCALE, ROUNDING);
        }

        // Try cross rate via base currency
        BigDecimal crossRate = getCrossRate(from, to, date);
        if (crossRate != null) {
            return crossRate;
        }

        log.warn("No FX rate found for {}/{} on {}, defaulting to 1.0", from, to, date);
        return BigDecimal.ONE;
    }

    /**
     * Get rate for today.
     */
    public BigDecimal getRate(String fromCcy, String toCcy) {
        return getRate(fromCcy, toCcy, LocalDate.now());
    }

    /**
     * Convert amount from one currency to another.
     */
    public BigDecimal convert(BigDecimal amount, String fromCcy, String toCcy, LocalDate date) {
        if (amount == null) return BigDecimal.ZERO;
        if (fromCcy == null || toCcy == null) return amount;
        if (fromCcy.equalsIgnoreCase(toCcy)) return amount;

        BigDecimal rate = getRate(fromCcy, toCcy, date);
        return amount.multiply(rate).setScale(SCALE, ROUNDING);
    }

    /**
     * Convert amount for today.
     */
    public BigDecimal convert(BigDecimal amount, String fromCcy, String toCcy) {
        return convert(amount, fromCcy, toCcy, LocalDate.now());
    }

    /**
     * Convert to base currency.
     */
    public BigDecimal convertToBase(BigDecimal amount, String fromCcy, LocalDate date) {
        return convert(amount, fromCcy, baseCurrency, date);
    }

    /**
     * Convert to base currency for today.
     */
    public BigDecimal convertToBase(BigDecimal amount, String fromCcy) {
        return convertToBase(amount, fromCcy, LocalDate.now());
    }

    /**
     * Update real-time rate (called by Price Service).
     */
    public void updateRealtimeRate(FxRateDTO rate) {
        if (rate != null && rate.currencyPair() != null) {
            realtimeRates.put(rate.currencyPair().toUpperCase(), rate);
        }
    }

    /**
     * Get real-time rate if available.
     */
    public Optional<FxRateDTO> getRealtimeRate(String currencyPair) {
        return Optional.ofNullable(realtimeRates.get(currencyPair.toUpperCase()));
    }

    /**
     * Get base currency.
     */
    public String getBaseCurrency() {
        return baseCurrency;
    }

    /**
     * Set base currency (for configuration).
     */
    public void setBaseCurrency(String currency) {
        this.baseCurrency = currency;
    }

    // ════════════════════════════════════════════════════════════════════════
    // INTERNAL METHODS
    // ════════════════════════════════════════════════════════════════════════

    private Optional<BigDecimal> getDirectRate(String pair, LocalDate date) {
        // Check real-time cache first
        FxRateDTO realtime = realtimeRates.get(pair);
        if (realtime != null && realtime.rate() != null) {
            return Optional.of(realtime.rate());
        }

        // Fall back to database
        return fxRateRepository.getRate(pair, date).map(FxRateDTO::rate);
    }

    private BigDecimal getCrossRate(String from, String to, LocalDate date) {
        // Cross rate via base currency (e.g., EUR->GBP via USD: EUR/USD / GBP/USD)
        String fromBase = from + baseCurrency;
        String toBase = to + baseCurrency;

        Optional<BigDecimal> fromRate = getDirectRate(fromBase, date);
        Optional<BigDecimal> toRate = getDirectRate(toBase, date);

        if (fromRate.isPresent() && toRate.isPresent() 
                && toRate.get().compareTo(BigDecimal.ZERO) != 0) {
            return fromRate.get().divide(toRate.get(), SCALE, ROUNDING);
        }

        // Try inverse: USD/EUR * USD/GBP
        String baseFrom = baseCurrency + from;
        String baseTo = baseCurrency + to;

        Optional<BigDecimal> baseFromRate = getDirectRate(baseFrom, date);
        Optional<BigDecimal> baseToRate = getDirectRate(baseTo, date);

        if (baseFromRate.isPresent() && baseToRate.isPresent() 
                && baseFromRate.get().compareTo(BigDecimal.ZERO) != 0) {
            return baseToRate.get().divide(baseFromRate.get(), SCALE, ROUNDING);
        }

        return null;
    }

    /**
     * Check if rate exists for a currency pair.
     */
    public boolean hasRate(String currencyPair) {
        return realtimeRates.containsKey(currencyPair.toUpperCase()) 
                || fxRateRepository.getLatestRate(currencyPair).isPresent();
    }

    /**
     * Get all available currency pairs.
     */
    public java.util.Set<String> getAvailablePairs() {
        java.util.Set<String> pairs = new java.util.HashSet<>(realtimeRates.keySet());
        pairs.addAll(fxRateRepository.getAllCurrencyPairs());
        return pairs;
    }

    /**
     * Clear real-time cache (for testing).
     */
    public void clearRealtimeCache() {
        realtimeRates.clear();
    }
}
