package com.vyshali.positionloader.service;

import com.vyshali.positionloader.dto.Dto;
import com.vyshali.positionloader.repository.DataRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 3 Enhancement #11: Reconciliation
 * <p>
 * Compares expected vs actual data to detect:
 * - Missing positions
 * - Count mismatches
 * - Large value changes (>20%)
 * - New/closed positions
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationService {

    private final DataRepository repo;
    private final AlertService alertService;
    private final MeterRegistry metrics;

    // Thresholds
    private static final double VALUE_CHANGE_ALERT_THRESHOLD = 0.20;  // 20%
    private static final int MIN_POSITIONS_FOR_PERCENT_CHECK = 10;

    // ═══════════════════════════════════════════════════════════════════════════
    // RECONCILIATION REPORT
    // ═══════════════════════════════════════════════════════════════════════════

    public record ReconciliationReport(Integer accountId, LocalDate businessDate, LocalDate previousDate,
                                       int currentCount, int previousCount, int newPositions, int closedPositions,
                                       int unchangedPositions, int increasedPositions, int decreasedPositions,
                                       List<Anomaly> anomalies, String status  // OK, WARNING, CRITICAL
    ) {
    }

    public record Anomaly(String type,      // COUNT_MISMATCH, LARGE_CHANGE, MISSING_DATA, etc.
                          String severity,  // WARNING, CRITICAL
                          String message, Integer productId, BigDecimal previousValue, BigDecimal currentValue) {
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RECONCILE ACCOUNT
    // ═══════════════════════════════════════════════════════════════════════════

    public ReconciliationReport reconcile(Integer accountId, LocalDate date) {
        LocalDate previousDate = date.minusDays(1);

        // Get positions
        List<Dto.Position> current = repo.getActivePositions(accountId, date);
        List<Dto.Position> previous = repo.getActivePositions(accountId, previousDate);

        // Build maps for comparison
        Map<Integer, Dto.Position> currentMap = new HashMap<>();
        Map<Integer, Dto.Position> previousMap = new HashMap<>();

        for (Dto.Position p : current) {
            currentMap.put(p.productId(), p);
        }
        for (Dto.Position p : previous) {
            previousMap.put(p.productId(), p);
        }

        // Compare
        List<Anomaly> anomalies = new ArrayList<>();
        int newPositions = 0;
        int closedPositions = 0;
        int unchangedPositions = 0;
        int increasedPositions = 0;
        int decreasedPositions = 0;

        // Check current positions
        for (Map.Entry<Integer, Dto.Position> entry : currentMap.entrySet()) {
            Integer productId = entry.getKey();
            Dto.Position curr = entry.getValue();
            Dto.Position prev = previousMap.get(productId);

            if (prev == null) {
                // New position
                newPositions++;
            } else {
                // Existing position - compare
                int qtyCompare = compareQuantity(prev.quantity(), curr.quantity());
                if (qtyCompare == 0) {
                    unchangedPositions++;
                } else if (qtyCompare > 0) {
                    increasedPositions++;
                } else {
                    decreasedPositions++;
                }

                // Check for large value change
                BigDecimal prevValue = calculateValue(prev);
                BigDecimal currValue = calculateValue(curr);
                double changePercent = calculateChangePercent(prevValue, currValue);

                if (Math.abs(changePercent) > VALUE_CHANGE_ALERT_THRESHOLD) {
                    anomalies.add(new Anomaly("LARGE_VALUE_CHANGE", Math.abs(changePercent) > 0.5 ? "CRITICAL" : "WARNING", String.format("Value changed %.1f%% for product %d", changePercent * 100, productId), productId, prevValue, currValue));
                }
            }
        }

        // Check for closed positions
        for (Integer productId : previousMap.keySet()) {
            if (!currentMap.containsKey(productId)) {
                closedPositions++;
            }
        }

        // Count anomaly checks
        if (previous.size() >= MIN_POSITIONS_FOR_PERCENT_CHECK) {
            double countChange = (double) (current.size() - previous.size()) / previous.size();
            if (Math.abs(countChange) > 0.3) {  // >30% change in position count
                anomalies.add(new Anomaly("COUNT_CHANGE", Math.abs(countChange) > 0.5 ? "CRITICAL" : "WARNING", String.format("Position count changed %.1f%% (%d → %d)", countChange * 100, previous.size(), current.size()), null, BigDecimal.valueOf(previous.size()), BigDecimal.valueOf(current.size())));
            }
        }

        // Check for missing today's data
        if (current.isEmpty() && !previous.isEmpty()) {
            anomalies.add(new Anomaly("MISSING_DATA", "CRITICAL", String.format("No positions loaded today, had %d yesterday", previous.size()), null, BigDecimal.valueOf(previous.size()), BigDecimal.ZERO));
        }

        // Determine overall status
        String status = "OK";
        if (anomalies.stream().anyMatch(a -> "CRITICAL".equals(a.severity()))) {
            status = "CRITICAL";
        } else if (!anomalies.isEmpty()) {
            status = "WARNING";
        }

        // Metrics
        metrics.counter("posloader.reconciliation.completed", "status", status).increment();
        metrics.gauge("posloader.reconciliation.anomalies", anomalies.size());

        ReconciliationReport report = new ReconciliationReport(accountId, date, previousDate, current.size(), previous.size(), newPositions, closedPositions, unchangedPositions, increasedPositions, decreasedPositions, anomalies, status);

        // Alert if issues found
        if ("CRITICAL".equals(status)) {
            alertService.critical("RECONCILIATION_FAILED", String.format("Account %d has %d critical anomalies", accountId, anomalies.size()), accountId.toString());
        } else if ("WARNING".equals(status)) {
            alertService.warning("RECONCILIATION_WARNING", String.format("Account %d has %d anomalies", accountId, anomalies.size()), accountId.toString());
        }

        log.info("Reconciliation for account {}: {} (new={}, closed={}, anomalies={})", accountId, status, newPositions, closedPositions, anomalies.size());

        return report;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BATCH RECONCILIATION (run after EOD)
    // ═══════════════════════════════════════════════════════════════════════════

    public List<ReconciliationReport> reconcileAllAccounts(LocalDate date) {
        List<Integer> accountIds = repo.getAccountsWithPositions(date);
        List<ReconciliationReport> reports = new ArrayList<>();

        for (Integer accountId : accountIds) {
            try {
                reports.add(reconcile(accountId, date));
            } catch (Exception e) {
                log.error("Reconciliation failed for account {}: {}", accountId, e.getMessage());
            }
        }

        // Summary metrics
        long critical = reports.stream().filter(r -> "CRITICAL".equals(r.status())).count();
        long warning = reports.stream().filter(r -> "WARNING".equals(r.status())).count();

        metrics.gauge("posloader.reconciliation.accounts_critical", critical);
        metrics.gauge("posloader.reconciliation.accounts_warning", warning);

        if (critical > 0) {
            alertService.critical("RECONCILIATION_BATCH", String.format("%d accounts have critical reconciliation issues", critical), "batch");
        }

        log.info("Batch reconciliation complete: {} accounts ({} critical, {} warning)", reports.size(), critical, warning);

        return reports;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private BigDecimal calculateValue(Dto.Position p) {
        if (p.quantity() == null || p.price() == null) return BigDecimal.ZERO;
        return p.quantity().multiply(p.price());
    }

    private double calculateChangePercent(BigDecimal prev, BigDecimal curr) {
        if (prev == null || prev.compareTo(BigDecimal.ZERO) == 0) {
            return curr != null && curr.compareTo(BigDecimal.ZERO) != 0 ? 1.0 : 0.0;
        }
        return curr.subtract(prev).divide(prev, 4, RoundingMode.HALF_UP).doubleValue();
    }

    private int compareQuantity(BigDecimal prev, BigDecimal curr) {
        if (prev == null) prev = BigDecimal.ZERO;
        if (curr == null) curr = BigDecimal.ZERO;
        return curr.compareTo(prev);
    }
}