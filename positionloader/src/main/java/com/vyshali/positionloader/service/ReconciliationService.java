package com.vyshali.positionloader.service;

import com.vyshali.common.service.AlertService;  // ✅ FROM COMMON MODULE
import com.vyshali.positionloader.config.LoaderConfig;
import com.vyshali.positionloader.dto.PositionDto;
import com.vyshali.positionloader.repository.DataRepository;
import com.vyshali.positionloader.repository.PositionRepository;
import com.vyshali.positionloader.repository.ReferenceDataRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Reconciliation service for comparing positions across sources.
 * Phase 3 feature: T vs T-1 comparison, tolerance checks, alerting.
 * 
 * ✅ Uses common module's AlertService for notifications
 */
@Slf4j
@Service
public class ReconciliationService {

    private final PositionRepository positionRepository;
    private final ReferenceDataRepository referenceDataRepository;
    private final DataRepository dataRepository;
    private final AlertService alertService;  // ✅ FROM COMMON MODULE
    private final LoaderConfig config;
    
    // Metrics
    private final Counter reconciliationsRun;
    private final Counter reconciliationBreaks;
    private final Counter reconciliationAlerts;

    public ReconciliationService(
            PositionRepository positionRepository,
            ReferenceDataRepository referenceDataRepository,
            DataRepository dataRepository,
            AlertService alertService,  // ✅ FROM COMMON MODULE
            LoaderConfig config,
            MeterRegistry meterRegistry) {
        this.positionRepository = positionRepository;
        this.referenceDataRepository = referenceDataRepository;
        this.dataRepository = dataRepository;
        this.alertService = alertService;
        this.config = config;
        
        this.reconciliationsRun = meterRegistry.counter("reconciliation.runs");
        this.reconciliationBreaks = meterRegistry.counter("reconciliation.breaks");
        this.reconciliationAlerts = meterRegistry.counter("reconciliation.alerts");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MAIN RECONCILIATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Run reconciliation for a single account.
     */
    public ReconciliationReport reconcile(int accountId, LocalDate businessDate) {
        reconciliationsRun.increment();
        log.info("Running reconciliation for account {} on {}", accountId, businessDate);

        LocalDate previousDate = businessDate.minusDays(1);
        
        // Get positions for both days
        List<PositionDto> currentPositions = positionRepository.findByAccountAndDate(accountId, businessDate);
        List<PositionDto> previousPositions = positionRepository.findByAccountAndDate(accountId, previousDate);

        if (currentPositions.isEmpty()) {
            log.warn("No positions found for account {} on {}", accountId, businessDate);
            return new ReconciliationReport(
                accountId, businessDate, "NO_DATA",
                0, 0, 0, List.of(), LocalDateTime.now()
            );
        }

        // Build position maps by productId
        Map<Integer, PositionDto> currentMap = currentPositions.stream()
            .collect(Collectors.toMap(PositionDto::productId, p -> p, (a, b) -> a));
        Map<Integer, PositionDto> previousMap = previousPositions.stream()
            .collect(Collectors.toMap(PositionDto::productId, p -> p, (a, b) -> a));

        List<ReconciliationBreak> breaks = new ArrayList<>();

        // Check for quantity changes beyond tolerance
        for (var entry : currentMap.entrySet()) {
            int productId = entry.getKey();
            PositionDto current = entry.getValue();
            PositionDto previous = previousMap.get(productId);

            if (previous == null) {
                // New position - might be intentional
                if (current.quantity().abs().compareTo(config.reconciliation().largePositionThreshold()) > 0) {
                    breaks.add(new ReconciliationBreak(
                        productId, "NEW_LARGE_POSITION",
                        BigDecimal.ZERO, current.quantity(),
                        current.quantity(), "Large new position appeared"
                    ));
                }
            } else {
                // Check quantity change
                BigDecimal change = current.quantity().subtract(previous.quantity()).abs();
                BigDecimal changePercent = previous.quantity().compareTo(BigDecimal.ZERO) != 0
                    ? change.divide(previous.quantity().abs(), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                    : BigDecimal.valueOf(100);

                if (changePercent.compareTo(config.reconciliation().quantityTolerancePercent()) > 0) {
                    breaks.add(new ReconciliationBreak(
                        productId, "QUANTITY_CHANGE",
                        previous.quantity(), current.quantity(),
                        change, String.format("%.2f%% change exceeds tolerance", changePercent)
                    ));
                }

                // Check price change
                if (current.price() != null && previous.price() != null) {
                    BigDecimal priceChange = current.price().subtract(previous.price()).abs();
                    BigDecimal priceChangePercent = previous.price().compareTo(BigDecimal.ZERO) != 0
                        ? priceChange.divide(previous.price().abs(), 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                        : BigDecimal.valueOf(100);

                    if (priceChangePercent.compareTo(config.reconciliation().priceTolerancePercent()) > 0) {
                        breaks.add(new ReconciliationBreak(
                            productId, "PRICE_CHANGE",
                            previous.price(), current.price(),
                            priceChange, String.format("%.2f%% price change", priceChangePercent)
                        ));
                    }
                }
            }
        }

        // Check for disappeared positions
        for (var entry : previousMap.entrySet()) {
            int productId = entry.getKey();
            if (!currentMap.containsKey(productId)) {
                PositionDto previous = entry.getValue();
                if (previous.quantity().abs().compareTo(BigDecimal.valueOf(100)) > 0) {
                    breaks.add(new ReconciliationBreak(
                        productId, "POSITION_DISAPPEARED",
                        previous.quantity(), BigDecimal.ZERO,
                        previous.quantity().abs(), "Position from T-1 no longer exists"
                    ));
                }
            }
        }

        // Determine status
        String status = determineStatus(breaks);
        
        // Record breaks metric
        reconciliationBreaks.increment(breaks.size());

        // Alert if critical
        if ("CRITICAL".equals(status)) {
            reconciliationAlerts.increment();
            alertService.critical("Reconciliation breaks detected",
                String.format("Account %d has %d critical breaks on %s", 
                    accountId, breaks.size(), businessDate));
        } else if ("WARNING".equals(status)) {
            alertService.warn("Reconciliation warnings",
                String.format("Account %d has %d warnings on %s", 
                    accountId, breaks.size(), businessDate));
        }

        // Audit log
        dataRepository.logAudit("RECONCILIATION", accountId, businessDate,
            String.format("Status: %s, Breaks: %d", status, breaks.size()));

        return new ReconciliationReport(
            accountId, businessDate, status,
            currentPositions.size(), previousPositions.size(), breaks.size(),
            breaks, LocalDateTime.now()
        );
    }

    /**
     * Run reconciliation for all active accounts.
     */
    public List<ReconciliationReport> reconcileAllAccounts(LocalDate businessDate) {
        log.info("Running reconciliation for all accounts on {}", businessDate);
        
        List<Integer> activeAccounts = referenceDataRepository.getActiveAccounts();
        
        return activeAccounts.stream()
            .filter(id -> !config.features().disabledAccounts().contains(id))
            .map(accountId -> {
                try {
                    return reconcile(accountId, businessDate);
                } catch (Exception e) {
                    log.error("Reconciliation failed for account {}: {}", accountId, e.getMessage());
                    return new ReconciliationReport(
                        accountId, businessDate, "ERROR",
                        0, 0, 0, List.of(), LocalDateTime.now()
                    );
                }
            })
            .toList();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PHASE 4 #19: POSITION DIFF
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Compute detailed position diff between two dates.
     */
    public PositionDiffReport computePositionDiff(int accountId, LocalDate currentDate, LocalDate previousDate) {
        log.info("Computing position diff for account {} between {} and {}", 
            accountId, previousDate, currentDate);

        List<PositionDto> currentPositions = positionRepository.findByAccountAndDate(accountId, currentDate);
        List<PositionDto> previousPositions = positionRepository.findByAccountAndDate(accountId, previousDate);

        Map<Integer, PositionDto> currentMap = currentPositions.stream()
            .collect(Collectors.toMap(PositionDto::productId, p -> p, (a, b) -> a));
        Map<Integer, PositionDto> previousMap = previousPositions.stream()
            .collect(Collectors.toMap(PositionDto::productId, p -> p, (a, b) -> a));

        List<PositionChange> added = new ArrayList<>();
        List<PositionChange> removed = new ArrayList<>();
        List<PositionChange> changed = new ArrayList<>();

        // Find added and changed
        for (var entry : currentMap.entrySet()) {
            int productId = entry.getKey();
            PositionDto current = entry.getValue();
            PositionDto previous = previousMap.get(productId);

            if (previous == null) {
                added.add(new PositionChange(
                    productId, null, current.quantity(), null, current.price(),
                    current.marketValueLocal(), "NEW"
                ));
            } else {
                BigDecimal qtyChange = current.quantity().subtract(previous.quantity());
                BigDecimal priceChange = current.price() != null && previous.price() != null
                    ? current.price().subtract(previous.price())
                    : BigDecimal.ZERO;
                BigDecimal mvChange = current.marketValueLocal().subtract(previous.marketValueLocal());

                if (qtyChange.compareTo(BigDecimal.ZERO) != 0 || 
                    priceChange.abs().compareTo(BigDecimal.valueOf(0.01)) > 0) {
                    changed.add(new PositionChange(
                        productId, previous.quantity(), current.quantity(),
                        previous.price(), current.price(), mvChange,
                        describeChange(qtyChange, priceChange)
                    ));
                }
            }
        }

        // Find removed
        for (var entry : previousMap.entrySet()) {
            int productId = entry.getKey();
            if (!currentMap.containsKey(productId)) {
                PositionDto previous = entry.getValue();
                removed.add(new PositionChange(
                    productId, previous.quantity(), null, previous.price(), null,
                    previous.marketValueLocal().negate(), "CLOSED"
                ));
            }
        }

        // Calculate totals
        BigDecimal totalMvCurrent = currentPositions.stream()
            .map(PositionDto::marketValueLocal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalMvPrevious = previousPositions.stream()
            .map(PositionDto::marketValueLocal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new PositionDiffReport(
            accountId, currentDate, previousDate,
            currentPositions.size(), previousPositions.size(),
            added, removed, changed,
            totalMvCurrent, totalMvPrevious,
            totalMvCurrent.subtract(totalMvPrevious),
            LocalDateTime.now()
        );
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SCHEDULED RECONCILIATION
    // ═══════════════════════════════════════════════════════════════════════════

    @Scheduled(cron = "${app.reconciliation.schedule:0 30 6 * * ?}")
    public void scheduledReconciliation() {
        if (!config.features().reconciliationEnabled()) {
            log.info("Scheduled reconciliation disabled");
            return;
        }

        log.info("Starting scheduled reconciliation");
        LocalDate businessDate = LocalDate.now();
        
        List<ReconciliationReport> reports = reconcileAllAccounts(businessDate);
        
        long critical = reports.stream().filter(r -> "CRITICAL".equals(r.status())).count();
        long warnings = reports.stream().filter(r -> "WARNING".equals(r.status())).count();
        
        log.info("Scheduled reconciliation complete: {} accounts, {} critical, {} warnings",
            reports.size(), critical, warnings);

        if (critical > 0) {
            alertService.critical("Daily Reconciliation Alert",
                String.format("%d accounts have critical breaks", critical));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    private String determineStatus(List<ReconciliationBreak> breaks) {
        if (breaks.isEmpty()) {
            return "OK";
        }
        
        boolean hasCritical = breaks.stream()
            .anyMatch(b -> "POSITION_DISAPPEARED".equals(b.breakType()) || 
                          "NEW_LARGE_POSITION".equals(b.breakType()));
        
        if (hasCritical || breaks.size() > 10) {
            return "CRITICAL";
        }
        
        return "WARNING";
    }

    private String describeChange(BigDecimal qtyChange, BigDecimal priceChange) {
        List<String> parts = new ArrayList<>();
        if (qtyChange.compareTo(BigDecimal.ZERO) != 0) {
            parts.add(String.format("Qty %+.0f", qtyChange));
        }
        if (priceChange.abs().compareTo(BigDecimal.valueOf(0.01)) > 0) {
            parts.add(String.format("Price %+.4f", priceChange));
        }
        return String.join(", ", parts);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RECORD DEFINITIONS
    // ═══════════════════════════════════════════════════════════════════════════

    public record ReconciliationReport(
        int accountId,
        LocalDate businessDate,
        String status,  // OK, WARNING, CRITICAL, ERROR, NO_DATA
        int currentPositionCount,
        int previousPositionCount,
        int breakCount,
        List<ReconciliationBreak> breaks,
        LocalDateTime runAt
    ) {}

    public record ReconciliationBreak(
        int productId,
        String breakType,  // QUANTITY_CHANGE, PRICE_CHANGE, NEW_LARGE_POSITION, POSITION_DISAPPEARED
        BigDecimal previousValue,
        BigDecimal currentValue,
        BigDecimal difference,
        String description
    ) {}

    public record PositionDiffReport(
        int accountId,
        LocalDate currentDate,
        LocalDate previousDate,
        int currentPositionCount,
        int previousPositionCount,
        List<PositionChange> added,
        List<PositionChange> removed,
        List<PositionChange> changed,
        BigDecimal totalMarketValueCurrent,
        BigDecimal totalMarketValuePrevious,
        BigDecimal marketValueChange,
        LocalDateTime computedAt
    ) {}

    public record PositionChange(
        int productId,
        BigDecimal previousQuantity,
        BigDecimal currentQuantity,
        BigDecimal previousPrice,
        BigDecimal currentPrice,
        BigDecimal marketValueChange,
        String changeType
    ) {}
}
