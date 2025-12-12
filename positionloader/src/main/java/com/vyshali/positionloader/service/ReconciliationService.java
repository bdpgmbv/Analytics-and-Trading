package com.vyshali.positionloader.service;

import com.vyshali.common.entity.Position;
import com.vyshali.common.repository.PositionRepository;
import com.vyshali.common.repository.ReferenceDataRepository;
import com.vyshali.common.service.AlertService;
import com.vyshali.positionloader.config.LoaderConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Service for reconciling positions between FXAN and MSPM.
 * Identifies discrepancies and generates alerts for investigation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationService {

    private final PositionRepository positionRepository;
    private final ReferenceDataRepository referenceDataRepository;
    private final MspmClientService mspmClientService;
    private final LoaderConfig config;
    private final AlertService alertService;
    private final MeterRegistry meterRegistry;

    // ═══════════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Run full reconciliation for all active accounts.
     * @param businessDate The business date to reconcile
     * @return Reconciliation summary
     */
    public ReconciliationSummary runFullReconciliation(LocalDate businessDate) {
        log.info("Starting full reconciliation for {}", businessDate);
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            // Get all active account IDs (using correct method name)
            List<Integer> activeAccounts = referenceDataRepository.getAllActiveAccountIds();
            log.info("Reconciling {} active accounts", activeAccounts.size());

            ReconciliationSummary summary = new ReconciliationSummary(businessDate);

            for (Integer accountId : activeAccounts) {
                try {
                    AccountReconciliationResult result = reconcileAccount(accountId, businessDate);
                    summary.addAccountResult(result);
                } catch (Exception e) {
                    log.error("Failed to reconcile account {}: {}", accountId, e.getMessage());
                    summary.addFailedAccount(accountId, e.getMessage());
                }
            }

            // Log and alert summary
            logSummary(summary);

            return summary;

        } finally {
            sample.stop(Timer.builder("reconciliation.full")
                    .tag("businessDate", businessDate.toString())
                    .register(meterRegistry));
        }
    }

    /**
     * Run reconciliation for a specific account.
     * @param accountId Account to reconcile
     * @param businessDate Business date
     * @return Account reconciliation result
     */
    public AccountReconciliationResult reconcileAccount(int accountId, LocalDate businessDate) {
        log.debug("Reconciling account {} for {}", accountId, businessDate);

        // Get FXAN positions
        List<Position> fxanPositions = positionRepository.findByAccountIdAndBusinessDate(accountId, businessDate);
        Map<String, Position> fxanMap = new HashMap<>();
        for (Position p : fxanPositions) {
            fxanMap.put(p.getSecurityId(), p);
        }

        // Get MSPM positions
        List<MspmPosition> mspmPositions = mspmClientService.getPositionsForAccount(accountId, businessDate);
        Map<String, MspmPosition> mspmMap = new HashMap<>();
        for (MspmPosition p : mspmPositions) {
            mspmMap.put(p.securityId(), p);
        }

        // Compare
        AccountReconciliationResult result = new AccountReconciliationResult(accountId, businessDate);

        // Check FXAN positions against MSPM
        for (Map.Entry<String, Position> entry : fxanMap.entrySet()) {
            String securityId = entry.getKey();
            Position fxan = entry.getValue();
            MspmPosition mspm = mspmMap.get(securityId);

            if (mspm == null) {
                result.addMismatch(new PositionMismatch(
                        securityId,
                        MismatchType.MISSING_IN_SOURCE,
                        "Position exists in FXAN but not in MSPM",
                        fxan.getQuantity(),
                        null
                ));
            } else {
                // Compare quantities
                comparePositions(fxan, mspm, result);
                mspmMap.remove(securityId); // Mark as processed
            }
        }

        // Check for positions in MSPM but not in FXAN
        for (Map.Entry<String, MspmPosition> entry : mspmMap.entrySet()) {
            result.addMismatch(new PositionMismatch(
                    entry.getKey(),
                    MismatchType.MISSING_IN_FXAN,
                    "Position exists in MSPM but not in FXAN",
                    null,
                    entry.getValue().quantity()
            ));
        }

        // Alert on significant mismatches
        if (result.hasCriticalMismatches()) {
            alertService.reconciliationMismatch(accountId,
                    String.format("%d critical mismatches found", result.getCriticalMismatchCount()));
        }

        meterRegistry.counter("reconciliation.accounts.processed").increment();
        meterRegistry.counter("reconciliation.mismatches",
                        "account", String.valueOf(accountId))
                .increment(result.getMismatches().size());

        return result;
    }

    /**
     * Async reconciliation for parallel processing.
     */
    @Async
    public CompletableFuture<AccountReconciliationResult> reconcileAccountAsync(int accountId, LocalDate businessDate) {
        return CompletableFuture.completedFuture(reconcileAccount(accountId, businessDate));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // COMPARISON LOGIC
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Compare FXAN and MSPM positions for discrepancies.
     */
    private void comparePositions(Position fxan, MspmPosition mspm, AccountReconciliationResult result) {
        String securityId = fxan.getSecurityId();

        // Get tolerance thresholds from config (using correct method chain)
        BigDecimal quantityTolerance = config.reconciliation().quantityTolerancePercent()
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        BigDecimal priceTolerance = config.reconciliation().priceTolerancePercent()
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        BigDecimal largePositionThreshold = config.reconciliation().largePositionThreshold();

        // Compare quantity
        if (fxan.getQuantity() != null && mspm.quantity() != null) {
            BigDecimal fxanQty = fxan.getQuantity();
            BigDecimal mspmQty = mspm.quantity();

            if (!isWithinTolerance(fxanQty, mspmQty, quantityTolerance)) {
                boolean isLarge = fxanQty.abs().compareTo(largePositionThreshold) > 0;

                result.addMismatch(new PositionMismatch(
                        securityId,
                        isLarge ? MismatchType.LARGE_QUANTITY_MISMATCH : MismatchType.QUANTITY_MISMATCH,
                        String.format("Quantity mismatch: FXAN=%s, MSPM=%s", fxanQty, mspmQty),
                        fxanQty,
                        mspmQty
                ));
            }
        }

        // Compare market value
        if (fxan.getMarketValue() != null && mspm.marketValue() != null) {
            BigDecimal fxanMv = fxan.getMarketValue();
            BigDecimal mspmMv = mspm.marketValue();

            if (!isWithinTolerance(fxanMv, mspmMv, priceTolerance)) {
                result.addMismatch(new PositionMismatch(
                        securityId,
                        MismatchType.MARKET_VALUE_MISMATCH,
                        String.format("Market value mismatch: FXAN=%s, MSPM=%s", fxanMv, mspmMv),
                        fxanMv,
                        mspmMv
                ));
            }
        }
    }

    /**
     * Check if two values are within tolerance percentage.
     */
    private boolean isWithinTolerance(BigDecimal value1, BigDecimal value2, BigDecimal tolerancePercent) {
        if (value1.compareTo(BigDecimal.ZERO) == 0 && value2.compareTo(BigDecimal.ZERO) == 0) {
            return true;
        }

        if (value1.compareTo(BigDecimal.ZERO) == 0 || value2.compareTo(BigDecimal.ZERO) == 0) {
            return false; // One is zero, other is not
        }

        BigDecimal diff = value1.subtract(value2).abs();
        BigDecimal avgAbs = value1.abs().add(value2.abs()).divide(BigDecimal.valueOf(2), 10, RoundingMode.HALF_UP);
        BigDecimal percentDiff = diff.divide(avgAbs, 10, RoundingMode.HALF_UP);

        return percentDiff.compareTo(tolerancePercent) <= 0;
    }

    /**
     * Log reconciliation summary.
     */
    private void logSummary(ReconciliationSummary summary) {
        log.info("Reconciliation complete for {}: accounts={}, mismatches={}, failed={}",
                summary.getBusinessDate(),
                summary.getTotalAccounts(),
                summary.getTotalMismatches(),
                summary.getFailedAccounts().size());

        if (summary.getTotalMismatches() > 0) {
            alertService.warning(AlertService.ALERT_RECONCILIATION_MISMATCH,
                    String.format("Reconciliation found %d mismatches across %d accounts",
                            summary.getTotalMismatches(), summary.getAccountsWithMismatches()),
                    "businessDate=" + summary.getBusinessDate());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DTOs AND ENUMS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * MSPM position representation.
     */
    public record MspmPosition(
            String securityId,
            BigDecimal quantity,
            BigDecimal marketValue,
            BigDecimal costBasis,
            String currency
    ) {}

    /**
     * Types of position mismatches.
     */
    public enum MismatchType {
        QUANTITY_MISMATCH,
        LARGE_QUANTITY_MISMATCH,
        MARKET_VALUE_MISMATCH,
        MISSING_IN_SOURCE,
        MISSING_IN_FXAN
    }

    /**
     * Individual position mismatch.
     */
    public record PositionMismatch(
            String securityId,
            MismatchType type,
            String description,
            BigDecimal fxanValue,
            BigDecimal mspmValue
    ) {
        public boolean isCritical() {
            return type == MismatchType.LARGE_QUANTITY_MISMATCH
                    || type == MismatchType.MISSING_IN_SOURCE
                    || type == MismatchType.MISSING_IN_FXAN;
        }
    }

    /**
     * Result for a single account reconciliation.
     */
    public static class AccountReconciliationResult {
        private final int accountId;
        private final LocalDate businessDate;
        private final List<PositionMismatch> mismatches = new ArrayList<>();

        public AccountReconciliationResult(int accountId, LocalDate businessDate) {
            this.accountId = accountId;
            this.businessDate = businessDate;
        }

        public void addMismatch(PositionMismatch mismatch) {
            mismatches.add(mismatch);
        }

        public int getAccountId() {
            return accountId;
        }

        public LocalDate getBusinessDate() {
            return businessDate;
        }

        public List<PositionMismatch> getMismatches() {
            return mismatches;
        }

        public boolean hasCriticalMismatches() {
            return mismatches.stream().anyMatch(PositionMismatch::isCritical);
        }

        public long getCriticalMismatchCount() {
            return mismatches.stream().filter(PositionMismatch::isCritical).count();
        }
    }

    /**
     * Summary of full reconciliation run.
     */
    public static class ReconciliationSummary {
        private final LocalDate businessDate;
        private final List<AccountReconciliationResult> results = new ArrayList<>();
        private final Map<Integer, String> failedAccounts = new HashMap<>();

        public ReconciliationSummary(LocalDate businessDate) {
            this.businessDate = businessDate;
        }

        public void addAccountResult(AccountReconciliationResult result) {
            results.add(result);
        }

        public void addFailedAccount(int accountId, String reason) {
            failedAccounts.put(accountId, reason);
        }

        public LocalDate getBusinessDate() {
            return businessDate;
        }

        public int getTotalAccounts() {
            return results.size() + failedAccounts.size();
        }

        public int getTotalMismatches() {
            return results.stream()
                    .mapToInt(r -> r.getMismatches().size())
                    .sum();
        }

        public int getAccountsWithMismatches() {
            return (int) results.stream()
                    .filter(r -> !r.getMismatches().isEmpty())
                    .count();
        }

        public Map<Integer, String> getFailedAccounts() {
            return failedAccounts;
        }

        public List<AccountReconciliationResult> getResults() {
            return results;
        }
    }
}