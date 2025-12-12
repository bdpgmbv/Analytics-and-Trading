package com.vyshali.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Utility class for financial calculations
 */
public final class CalculationUtil {

    private CalculationUtil() {
        // Utility class - no instantiation
    }

    public static final int DEFAULT_SCALE = 4;
    public static final int PERCENT_SCALE = 2;
    public static final RoundingMode DEFAULT_ROUNDING = RoundingMode.HALF_UP;

    /**
     * Safe addition (handles nulls)
     */
    public static BigDecimal add(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) return BigDecimal.ZERO;
        if (a == null) return b;
        if (b == null) return a;
        return a.add(b);
    }

    /**
     * Safe subtraction (handles nulls)
     */
    public static BigDecimal subtract(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) return BigDecimal.ZERO;
        if (a == null) return b.negate();
        if (b == null) return a;
        return a.subtract(b);
    }

    /**
     * Safe multiplication (handles nulls)
     */
    public static BigDecimal multiply(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) return null;
        return a.multiply(b).setScale(DEFAULT_SCALE, DEFAULT_ROUNDING);
    }

    /**
     * Safe division (handles nulls and division by zero)
     */
    public static BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null) return null;
        if (denominator.compareTo(BigDecimal.ZERO) == 0) return null;
        return numerator.divide(denominator, DEFAULT_SCALE, DEFAULT_ROUNDING);
    }

    /**
     * Calculate percentage
     */
    public static BigDecimal percentage(BigDecimal value, BigDecimal total) {
        BigDecimal ratio = divide(value, total);
        if (ratio == null) return null;
        return ratio.multiply(BigDecimal.valueOf(100)).setScale(PERCENT_SCALE, DEFAULT_ROUNDING);
    }

    /**
     * Sum a collection of BigDecimals
     */
    public static BigDecimal sum(Collection<BigDecimal> values) {
        if (values == null || values.isEmpty()) return BigDecimal.ZERO;
        return values.stream()
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calculate market value: quantity * price
     */
    public static BigDecimal calculateMarketValue(BigDecimal quantity, BigDecimal price) {
        return multiply(quantity, price);
    }

    /**
     * Calculate market value in base currency: quantity * price * fxRate
     */
    public static BigDecimal calculateMarketValueBase(BigDecimal quantity, BigDecimal price, BigDecimal fxRate) {
        BigDecimal marketValueLocal = calculateMarketValue(quantity, price);
        return multiply(marketValueLocal, fxRate);
    }

    /**
     * Calculate unrealized P&L: marketValue - costBasis
     */
    public static BigDecimal calculateUnrealizedPnl(BigDecimal marketValue, BigDecimal costBasis) {
        return subtract(marketValue, costBasis);
    }

    /**
     * Calculate exposure amount: marketValue * weightPercent / 100
     */
    public static BigDecimal calculateExposureAmount(BigDecimal marketValue, BigDecimal weightPercent) {
        if (marketValue == null || weightPercent == null) return null;
        return marketValue.multiply(weightPercent)
                .divide(BigDecimal.valueOf(100), DEFAULT_SCALE, DEFAULT_ROUNDING);
    }

    /**
     * Calculate hedged vs unhedged exposure
     */
    public static BigDecimal calculateUnhedgedExposure(BigDecimal totalExposure, BigDecimal hedgedAmount) {
        return subtract(totalExposure, hedgedAmount);
    }

    /**
     * Calculate net exposure by currency (aggregates positive and negative exposures)
     */
    public static Map<String, BigDecimal> calculateNetExposureByCurrency(
            Collection<Map.Entry<String, BigDecimal>> exposures) {
        return exposures.stream()
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                Map.Entry::getValue,
                                BigDecimal::add
                        )
                ));
    }

    /**
     * Calculate NAV impact from FX move
     * Returns the P&L impact for a 1% move in FX rate
     */
    public static BigDecimal calculateFxSensitivity(BigDecimal exposureAmount) {
        if (exposureAmount == null) return null;
        return exposureAmount.multiply(BigDecimal.valueOf(0.01))
                .setScale(DEFAULT_SCALE, DEFAULT_ROUNDING);
    }

    /**
     * Check if value is effectively zero (within tolerance)
     */
    public static boolean isEffectivelyZero(BigDecimal value, BigDecimal tolerance) {
        if (value == null) return true;
        if (tolerance == null) tolerance = BigDecimal.valueOf(0.0001);
        return value.abs().compareTo(tolerance) <= 0;
    }

    /**
     * Round to display precision
     */
    public static BigDecimal roundForDisplay(BigDecimal value, int decimals) {
        if (value == null) return null;
        return value.setScale(decimals, DEFAULT_ROUNDING);
    }

    /**
     * Format as percentage string
     */
    public static String formatAsPercent(BigDecimal value) {
        if (value == null) return "0.00%";
        return value.setScale(PERCENT_SCALE, DEFAULT_ROUNDING).toPlainString() + "%";
    }

    /**
     * Calculate weighted average
     */
    public static BigDecimal weightedAverage(Collection<BigDecimal[]> valuesAndWeights) {
        if (valuesAndWeights == null || valuesAndWeights.isEmpty()) return null;
        
        BigDecimal sumProduct = BigDecimal.ZERO;
        BigDecimal sumWeights = BigDecimal.ZERO;
        
        for (BigDecimal[] vw : valuesAndWeights) {
            if (vw.length >= 2 && vw[0] != null && vw[1] != null) {
                sumProduct = sumProduct.add(vw[0].multiply(vw[1]));
                sumWeights = sumWeights.add(vw[1]);
            }
        }
        
        return divide(sumProduct, sumWeights);
    }
}
