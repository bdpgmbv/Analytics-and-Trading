package com.vyshali.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Utility class for currency operations
 */
public final class CurrencyUtil {

    private CurrencyUtil() {
        // Utility class - no instantiation
    }

    // Standard scale for currency amounts
    public static final int AMOUNT_SCALE = 4;
    public static final int RATE_SCALE = 8;
    public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    // G10 currencies
    private static final Set<String> G10_CURRENCIES = Set.of(
            "USD", "EUR", "GBP", "JPY", "CHF", "AUD", "CAD", "NZD", "NOK", "SEK"
    );

    // Currency decimal places (for proper rounding)
    private static final Map<String, Integer> CURRENCY_DECIMALS = new HashMap<>();
    static {
        CURRENCY_DECIMALS.put("JPY", 0);
        CURRENCY_DECIMALS.put("KRW", 0);
        CURRENCY_DECIMALS.put("VND", 0);
    }

    /**
     * Check if currency is a G10 currency
     */
    public static boolean isG10Currency(String currencyCode) {
        return currencyCode != null && G10_CURRENCIES.contains(currencyCode.toUpperCase());
    }

    /**
     * Get the number of decimal places for a currency
     */
    public static int getDecimalPlaces(String currencyCode) {
        if (currencyCode == null) return 2;
        return CURRENCY_DECIMALS.getOrDefault(currencyCode.toUpperCase(), 2);
    }

    /**
     * Create currency pair string (e.g., "EURUSD")
     */
    public static String createCurrencyPair(String baseCurrency, String quoteCurrency) {
        return baseCurrency.toUpperCase() + quoteCurrency.toUpperCase();
    }

    /**
     * Parse currency pair into base and quote currencies
     * Returns array: [baseCurrency, quoteCurrency]
     */
    public static String[] parseCurrencyPair(String currencyPair) {
        if (currencyPair == null || currencyPair.length() != 6 && currencyPair.length() != 7) {
            throw new IllegalArgumentException("Invalid currency pair: " + currencyPair);
        }
        // Handle both "EURUSD" and "EUR/USD" formats
        String pair = currencyPair.replace("/", "").toUpperCase();
        return new String[] {
                pair.substring(0, 3),
                pair.substring(3, 6)
        };
    }

    /**
     * Convert amount from one currency to another
     */
    public static BigDecimal convert(BigDecimal amount, BigDecimal fxRate) {
        if (amount == null || fxRate == null) return null;
        return amount.multiply(fxRate).setScale(AMOUNT_SCALE, ROUNDING_MODE);
    }

    /**
     * Calculate inverse FX rate
     */
    public static BigDecimal invertRate(BigDecimal rate) {
        if (rate == null || rate.compareTo(BigDecimal.ZERO) == 0) return null;
        return BigDecimal.ONE.divide(rate, RATE_SCALE, ROUNDING_MODE);
    }

    /**
     * Round amount to appropriate currency precision
     */
    public static BigDecimal roundAmount(BigDecimal amount, String currencyCode) {
        if (amount == null) return null;
        int decimals = getDecimalPlaces(currencyCode);
        return amount.setScale(decimals, ROUNDING_MODE);
    }

    /**
     * Validate currency code
     */
    public static boolean isValidCurrencyCode(String currencyCode) {
        if (currencyCode == null || currencyCode.length() != 3) {
            return false;
        }
        try {
            Currency.getInstance(currencyCode.toUpperCase());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Get currency display name
     */
    public static String getCurrencyDisplayName(String currencyCode) {
        try {
            return Currency.getInstance(currencyCode.toUpperCase()).getDisplayName();
        } catch (Exception e) {
            return currencyCode;
        }
    }

    /**
     * Calculate forward rate from spot rate and forward points
     */
    public static BigDecimal calculateForwardRate(BigDecimal spotRate, BigDecimal forwardPoints) {
        if (spotRate == null) return null;
        if (forwardPoints == null) return spotRate;
        return spotRate.add(forwardPoints).setScale(RATE_SCALE, ROUNDING_MODE);
    }

    /**
     * Calculate forward points from spot and forward rates
     */
    public static BigDecimal calculateForwardPoints(BigDecimal spotRate, BigDecimal forwardRate) {
        if (spotRate == null || forwardRate == null) return null;
        return forwardRate.subtract(spotRate).setScale(RATE_SCALE, ROUNDING_MODE);
    }
}
