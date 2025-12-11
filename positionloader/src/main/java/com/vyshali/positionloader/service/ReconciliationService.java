package com.vyshali.positionloader.service;

import com.vyshali.positionloader.dto.PositionDto;
import com.vyshali.positionloader.repository.DataRepository;
import com.vyshali.positionloader.repository.ReferenceDataRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for position reconciliation.
 */
@Service
public class ReconciliationService {
    
    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);
    
    private final DataRepository dataRepository;
    private final ReferenceDataRepository referenceDataRepository;
    private final AlertService alertService;
    private final MeterRegistry meterRegistry;
    
    public ReconciliationService(
            DataRepository dataRepository,
            ReferenceDataRepository referenceDataRepository,
            AlertService alertService,
            MeterRegistry meterRegistry) {
        this.dataRepository = dataRepository;
        this.referenceDataRepository = referenceDataRepository;
        this.alertService = alertService;
        this.meterRegistry = meterRegistry;
    }
    
    /**
     * Reconcile positions for an account.
     */
    public ReconciliationReport reconcile(int accountId, LocalDate businessDate) {
        log.info("Reconciling account {} for date {}", accountId, businessDate);
        
        List<PositionDto> positions = dataRepository.findPositions(accountId, businessDate);
        
        if (positions.isEmpty()) {
            return new ReconciliationReport(
                accountId, businessDate, "NO_DATA", 0, 0, 
                BigDecimal.ZERO, "No positions found for reconciliation", List.of());
        }
        
        List<ReconciliationDiff> diffs = new ArrayList<>();
        int mismatchCount = 0;
        BigDecimal totalDifference = BigDecimal.ZERO;
        
        // Compare with previous day
        LocalDate previousDate = businessDate.minusDays(1);
        List<PositionDto> previousPositions = dataRepository.findPositions(accountId, previousDate);
        
        Map<Integer, PositionDto> prevMap = previousPositions.stream()
            .collect(Collectors.toMap(PositionDto::productId, p -> p, (a, b) -> a));
        
        for (PositionDto current : positions) {
            PositionDto previous = prevMap.get(current.productId());
            
            if (previous != null) {
                BigDecimal qtyDiff = current.quantity().subtract(previous.quantity());
                if (qtyDiff.abs().compareTo(BigDecimal.ZERO) > 0) {
                    diffs.add(new ReconciliationDiff(
                        current.productId(),
                        "QUANTITY_CHANGE",
                        previous.quantity(),
                        current.quantity(),
                        qtyDiff
                    ));
                    totalDifference = totalDifference.add(qtyDiff.abs());
                    mismatchCount++;
                }
            } else {
                // New position
                diffs.add(new ReconciliationDiff(
                    current.productId(),
                    "NEW_POSITION",
                    BigDecimal.ZERO,
                    current.quantity(),
                    current.quantity()
                ));
                mismatchCount++;
            }
        }
        
        // Check for closed positions
        for (PositionDto previous : previousPositions) {
            boolean found = positions.stream()
                .anyMatch(p -> p.productId() == previous.productId());
            if (!found) {
                diffs.add(new ReconciliationDiff(
                    previous.productId(),
                    "CLOSED_POSITION",
                    previous.quantity(),
                    BigDecimal.ZERO,
                    previous.quantity().negate()
                ));
                mismatchCount++;
            }
        }
        
        // Determine status
        String status;
        if (mismatchCount == 0) {
            status = "OK";
        } else if (mismatchCount <= 5) {
            status = "WARNING";
        } else {
            status = "CRITICAL";
            alertService.reconciliationMismatch(accountId, mismatchCount);
        }
        
        meterRegistry.counter("reconciliation.runs", "status", status).increment();
        
        return new ReconciliationReport(
            accountId, businessDate, status, positions.size(), mismatchCount,
            totalDifference, null, diffs);
    }
    
    /**
     * Reconcile all accounts.
     */
    public List<ReconciliationReport> reconcileAllAccounts(LocalDate businessDate) {
        log.info("Reconciling all accounts for date {}", businessDate);
        
        List<Integer> accountIds = referenceDataRepository.getAllActiveAccountIds();
        
        return accountIds.stream()
            .map(accountId -> reconcile(accountId, businessDate))
            .toList();
    }
    
    /**
     * Compute position diff between two dates.
     */
    public PositionDiffReport computePositionDiff(int accountId, LocalDate currentDate, LocalDate previousDate) {
        log.debug("Computing position diff for account {} between {} and {}", 
            accountId, previousDate, currentDate);
        
        List<PositionDto> currentPositions = dataRepository.findPositions(accountId, currentDate);
        List<PositionDto> previousPositions = dataRepository.findPositions(accountId, previousDate);
        
        Map<Integer, PositionDto> currMap = currentPositions.stream()
            .collect(Collectors.toMap(PositionDto::productId, p -> p, (a, b) -> a));
        Map<Integer, PositionDto> prevMap = previousPositions.stream()
            .collect(Collectors.toMap(PositionDto::productId, p -> p, (a, b) -> a));
        
        List<PositionChange> changes = new ArrayList<>();
        
        // Changes and new positions
        for (PositionDto current : currentPositions) {
            PositionDto previous = prevMap.get(current.productId());
            
            if (previous == null) {
                changes.add(new PositionChange(
                    current.productId(),
                    ChangeType.ADDED,
                    BigDecimal.ZERO,
                    current.quantity(),
                    current.quantity()
                ));
            } else {
                BigDecimal diff = current.quantity().subtract(previous.quantity());
                if (diff.compareTo(BigDecimal.ZERO) != 0) {
                    changes.add(new PositionChange(
                        current.productId(),
                        ChangeType.MODIFIED,
                        previous.quantity(),
                        current.quantity(),
                        diff
                    ));
                }
            }
        }
        
        // Removed positions
        for (PositionDto previous : previousPositions) {
            if (!currMap.containsKey(previous.productId())) {
                changes.add(new PositionChange(
                    previous.productId(),
                    ChangeType.REMOVED,
                    previous.quantity(),
                    BigDecimal.ZERO,
                    previous.quantity().negate()
                ));
            }
        }
        
        return new PositionDiffReport(
            accountId, currentDate, previousDate,
            currentPositions.size(), previousPositions.size(),
            changes.size(), changes);
    }
    
    /**
     * Reconciliation report.
     */
    public record ReconciliationReport(
        int accountId,
        LocalDate businessDate,
        String status,
        int positionCount,
        int mismatchCount,
        BigDecimal totalDifference,
        String message,
        List<ReconciliationDiff> diffs
    ) {}
    
    /**
     * Reconciliation difference.
     */
    public record ReconciliationDiff(
        int productId,
        String diffType,
        BigDecimal previousValue,
        BigDecimal currentValue,
        BigDecimal difference
    ) {}
    
    /**
     * Position diff report.
     */
    public record PositionDiffReport(
        int accountId,
        LocalDate currentDate,
        LocalDate previousDate,
        int currentPositionCount,
        int previousPositionCount,
        int changeCount,
        List<PositionChange> changes
    ) {}
    
    /**
     * Position change.
     */
    public record PositionChange(
        int productId,
        ChangeType changeType,
        BigDecimal previousQuantity,
        BigDecimal currentQuantity,
        BigDecimal difference
    ) {}
    
    /**
     * Change type.
     */
    public enum ChangeType {
        ADDED, MODIFIED, REMOVED
    }
}
