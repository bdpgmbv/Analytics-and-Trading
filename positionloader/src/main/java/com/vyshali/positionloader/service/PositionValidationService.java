package com.vyshali.positionloader.service;

/*
 * 12/09/2025 - 3:32 PM
 * @author Vyshali Prabananth Lal
 */

/*
 * CRITICAL FIX #2: Data Validation Layer
 *
 * Problem: Corrupt MSPM data (including zero prices) saved without checks
 * Issue #1: "Prices going to zero as price service itself is down"
 *
 * Solution: Comprehensive validation before saving to database
 *
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.positionloader.dto.AccountSnapshotDTO;
import com.vyshali.positionloader.dto.PositionDetailDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class PositionValidationService {

    private static final Logger log = LoggerFactory.getLogger(PositionValidationService.class);

    @Value("${validation.max-quantity:1000000000}")
    private BigDecimal maxQuantity;

    @Value("${validation.max-price:100000}")
    private BigDecimal maxPrice;

    @Value("${validation.max-market-value:10000000000}")
    private BigDecimal maxMarketValue;

    @Value("${validation.zero-price-threshold-pct:10}")
    private double zeroPriceThresholdPct;

    @Value("${validation.suspicious-change-pct:50}")
    private double suspiciousChangePct;

    private static final Set<String> INVALID_TICKERS = Set.of("NULL", "N/A", "UNKNOWN", "TEST", "DUMMY", "");

    private static final Pattern VALID_TICKER_PATTERN = Pattern.compile("^[A-Z0-9./\\-]{1,20}$");

    /**
     * Main validation entry point
     */
    public ValidationResult validateSnapshot(AccountSnapshotDTO snapshot) {
        ValidationResult result = new ValidationResult(snapshot.accountId());

        validateAccount(snapshot, result);

        if (snapshot.positions() != null) {
            for (int i = 0; i < snapshot.positions().size(); i++) {
                validatePosition(snapshot.positions().get(i), i, result);
            }

            validateCrossPosition(snapshot.positions(), result);
        }

        if (result.hasErrors()) {
            log.warn("Validation failed for account {}: {} errors, {} warnings", snapshot.accountId(), result.errorCount(), result.warningCount());
        }

        return result;
    }

    private void validateAccount(AccountSnapshotDTO snapshot, ValidationResult result) {
        if (snapshot.accountId() == null || snapshot.accountId() <= 0) {
            result.addError("INVALID_ACCOUNT_ID", "Account ID is null or invalid");
        }

        if (snapshot.status() == null || "Unavailable".equals(snapshot.status())) {
            result.addError("INVALID_STATUS", "Account status is unavailable");
        }

        if (snapshot.positions() == null || snapshot.positions().isEmpty()) {
            result.addWarning("EMPTY_POSITIONS", "Account has no positions");
        }
    }

    private void validatePosition(PositionDetailDTO position, int index, ValidationResult result) {
        String prefix = "Position[" + index + "] ";

        if (position.productId() == null) {
            result.addError("NULL_PRODUCT_ID", prefix + "productId is null");
        }

        // CRITICAL: ZERO PRICE DETECTION (Issue #1)
        if (position.price() == null) {
            result.addError("NULL_PRICE", prefix + "price is null");
        } else if (position.price().signum() == 0) {
            result.addError("ZERO_PRICE", prefix + "price is ZERO - Price Service may be down! " + "Product: " + position.ticker());
        } else if (position.price().signum() < 0) {
            result.addError("NEGATIVE_PRICE", prefix + "price is negative: " + position.price());
        } else if (position.price().compareTo(maxPrice) > 0) {
            result.addWarning("EXTREME_PRICE", prefix + "price exceeds threshold: " + position.price());
        }

        if (position.quantity() == null) {
            result.addError("NULL_QUANTITY", prefix + "quantity is null");
        } else if (position.quantity().abs().compareTo(maxQuantity) > 0) {
            result.addWarning("EXTREME_QUANTITY", prefix + "quantity exceeds threshold: " + position.quantity());
        }

        if (position.marketValue() != null) {
            if (position.marketValue().abs().compareTo(maxMarketValue) > 0) {
                result.addWarning("EXTREME_MARKET_VALUE", prefix + "market value exceeds threshold: " + position.marketValue());
            }

            if (position.quantity() != null && position.price() != null && position.price().signum() != 0) {
                BigDecimal calculated = position.quantity().multiply(position.price());
                BigDecimal diff = calculated.subtract(position.marketValue()).abs();
                BigDecimal tolerance = position.marketValue().abs().multiply(new BigDecimal("0.01"));

                if (diff.compareTo(tolerance) > 0) {
                    result.addWarning("MARKET_VALUE_MISMATCH", prefix + "marketValue doesn't match qty*price. Expected: " + calculated + ", Actual: " + position.marketValue());
                }
            }
        }

        if (position.ticker() == null || position.ticker().isBlank()) {
            result.addWarning("MISSING_TICKER", prefix + "ticker is missing");
        } else if (INVALID_TICKERS.contains(position.ticker().toUpperCase())) {
            result.addError("INVALID_TICKER", prefix + "ticker is invalid: " + position.ticker());
        } else if (!VALID_TICKER_PATTERN.matcher(position.ticker()).matches()) {
            result.addWarning("SUSPICIOUS_TICKER", prefix + "ticker format suspicious: " + position.ticker());
        }

        if (position.currency() == null || position.currency().length() != 3) {
            result.addWarning("INVALID_CURRENCY", prefix + "currency invalid: " + position.currency());
        }
    }

    private void validateCrossPosition(List<PositionDetailDTO> positions, ValidationResult result) {
        if (positions == null || positions.isEmpty()) return;

        // CRITICAL: ZERO PRICE THRESHOLD (Issue #1)
        long zeroPriceCount = positions.stream().filter(p -> p.price() != null && p.price().signum() == 0).count();

        double zeroPricePct = (double) zeroPriceCount / positions.size() * 100;

        if (zeroPricePct > zeroPriceThresholdPct) {
            result.addError("ZERO_PRICE_THRESHOLD_EXCEEDED", String.format("%.1f%% of positions have zero price (%d/%d). " + "PRICE SERVICE MAY BE DOWN!", zeroPricePct, zeroPriceCount, positions.size()));
        } else if (zeroPriceCount > 0) {
            result.addWarning("ZERO_PRICES_DETECTED", String.format("%d positions have zero price", zeroPriceCount));
        }

        Map<Integer, Long> productCounts = positions.stream().filter(p -> p.productId() != null).collect(Collectors.groupingBy(PositionDetailDTO::productId, Collectors.counting()));

        List<Integer> duplicates = productCounts.entrySet().stream().filter(e -> e.getValue() > 1).map(Map.Entry::getKey).toList();

        if (!duplicates.isEmpty()) {
            result.addWarning("DUPLICATE_PRODUCTS", "Duplicate product IDs found: " + duplicates);
        }

        BigDecimal totalExposure = positions.stream().filter(p -> p.marketValue() != null).map(p -> p.marketValue().abs()).reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalExposure.compareTo(new BigDecimal("100000000000")) > 0) {
            result.addWarning("EXTREME_TOTAL_EXPOSURE", "Total exposure exceeds $100B: " + totalExposure);
        }

        if (totalExposure.signum() > 0) {
            for (PositionDetailDTO pos : positions) {
                if (pos.marketValue() != null) {
                    BigDecimal pct = pos.marketValue().abs().divide(totalExposure, 4, java.math.RoundingMode.HALF_UP).multiply(new BigDecimal("100"));

                    if (pct.compareTo(new BigDecimal("50")) > 0) {
                        result.addWarning("CONCENTRATION_RISK", String.format("Position %s represents %.1f%% of total exposure", pos.ticker(), pct.doubleValue()));
                    }
                }
            }
        }
    }

    public ValidationResult validateChanges(List<PositionDetailDTO> oldPositions, List<PositionDetailDTO> newPositions, Integer accountId) {

        ValidationResult result = new ValidationResult(accountId);

        if (oldPositions == null || oldPositions.isEmpty()) {
            return result;
        }

        Map<Integer, PositionDetailDTO> oldMap = oldPositions.stream().filter(p -> p.productId() != null).collect(Collectors.toMap(PositionDetailDTO::productId, p -> p, (a, b) -> a));

        for (PositionDetailDTO newPos : newPositions) {
            if (newPos.productId() == null) continue;

            PositionDetailDTO oldPos = oldMap.get(newPos.productId());
            if (oldPos == null) continue;

            if (oldPos.quantity() != null && newPos.quantity() != null && oldPos.quantity().signum() != 0) {

                BigDecimal changePct = newPos.quantity().subtract(oldPos.quantity()).abs().divide(oldPos.quantity().abs(), 4, java.math.RoundingMode.HALF_UP).multiply(new BigDecimal("100"));

                if (changePct.compareTo(new BigDecimal(suspiciousChangePct)) > 0) {
                    result.addWarning("SUSPICIOUS_QUANTITY_CHANGE", String.format("Position %s quantity changed by %.1f%% (from %s to %s)", newPos.ticker(), changePct.doubleValue(), oldPos.quantity(), newPos.quantity()));
                }
            }

            if (oldPos.price() != null && oldPos.price().signum() > 0 && newPos.price() != null && newPos.price().signum() == 0) {

                result.addError("PRICE_DROPPED_TO_ZERO", String.format("Position %s price dropped from %s to ZERO! " + "Price Service may have failed.", newPos.ticker(), oldPos.price()));
            }
        }

        return result;
    }

    public boolean quickValidate(PositionDetailDTO position) {
        if (position == null) return false;
        if (position.productId() == null) return false;
        if (position.price() == null || position.price().signum() <= 0) return false;
        if (position.quantity() == null) return false;
        return true;
    }

    // Validation Result Class
    public static class ValidationResult {
        private final Integer accountId;
        private final List<ValidationIssue> errors = new ArrayList<>();
        private final List<ValidationIssue> warnings = new ArrayList<>();

        public ValidationResult(Integer accountId) {
            this.accountId = accountId;
        }

        public void addError(String code, String message) {
            errors.add(new ValidationIssue(code, message, Severity.ERROR));
            log.error("Validation ERROR [Account {}]: {} - {}", accountId, code, message);
        }

        public void addWarning(String code, String message) {
            warnings.add(new ValidationIssue(code, message, Severity.WARNING));
            log.warn("Validation WARNING [Account {}]: {} - {}", accountId, code, message);
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }

        public int errorCount() {
            return errors.size();
        }

        public int warningCount() {
            return warnings.size();
        }

        public List<ValidationIssue> getErrors() {
            return Collections.unmodifiableList(errors);
        }

        public List<ValidationIssue> getWarnings() {
            return Collections.unmodifiableList(warnings);
        }

        public String errorSummary() {
            return errors.stream().map(ValidationIssue::code).collect(Collectors.joining(", "));
        }

        public boolean isZeroPriceAlert() {
            return errors.stream().anyMatch(e -> e.code().contains("ZERO_PRICE"));
        }
    }

    public record ValidationIssue(String code, String message, Severity severity) {
    }

    public enum Severity {ERROR, WARNING}
}