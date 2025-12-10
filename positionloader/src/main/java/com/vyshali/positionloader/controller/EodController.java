package com.vyshali.positionloader.controller;

/*
 * SIMPLIFIED: Merged EodController + EodService
 *
 * BEFORE:
 *   EodController → EodService → JdbcTemplate/Repository
 *   (EodService was just a pass-through for read-only queries)
 *
 * AFTER:
 *   EodController → Repository (for queries)
 *   EodController → SnapshotService (for mutations)
 *
 * PRINCIPLE:
 *   - Don't create service layers that just delegate
 *   - Services add value for: transactions, business logic, orchestration
 *   - Simple queries can go directly to repository
 *
 * WHAT WAS REMOVED:
 *   - EodService.java (DELETE this file)
 *   - Pass-through methods like getEodStatus(), getEodHistory()
 */

import com.vyshali.positionloader.dto.EodStatusDTO;
import com.vyshali.positionloader.dto.PositionDTO;
import com.vyshali.positionloader.repository.EodRepository;
import com.vyshali.positionloader.repository.PositionRepository;
import com.vyshali.positionloader.service.SnapshotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/eod")
@RequiredArgsConstructor
@Tag(name = "EOD", description = "End-of-day position operations")
public class EodController {

    // Direct repository access for read-only queries
    private final EodRepository eodRepository;
    private final PositionRepository positionRepository;

    // Service only for mutations (business logic, transactions)
    private final SnapshotService snapshotService;

    // ═══════════════════════════════════════════════════════════════════════════
    // MUTATIONS - Use SnapshotService (has business logic)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Trigger EOD processing for an account.
     * Uses SnapshotService because it involves:
     * - Fetching from MSPM
     * - Database transactions
     * - Kafka publishing
     * - Error handling
     */
    @PostMapping("/trigger/{accountId}")
    @Operation(summary = "Trigger EOD processing for an account")
    @ApiResponse(responseCode = "200", description = "EOD triggered successfully")
    @ApiResponse(responseCode = "500", description = "EOD processing failed")
    public ResponseEntity<?> triggerEod(@Parameter(description = "Account ID") @PathVariable Integer accountId, @Parameter(description = "Business date (defaults to today)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {

        LocalDate date = businessDate != null ? businessDate : LocalDate.now();
        log.info("EOD trigger requested: accountId={}, date={}", accountId, date);

        try {
            snapshotService.processEod(accountId);
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "accountId", accountId, "businessDate", date));
        } catch (Exception e) {
            log.error("EOD failed for account {}: {}", accountId, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("status", "FAILED", "accountId", accountId, "error", e.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // QUERIES - Direct repository access (no service layer needed)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get EOD status for an account.
     * Direct repository call - no business logic needed.
     */
    @GetMapping("/status/{accountId}")
    @Operation(summary = "Get EOD status for an account")
    public ResponseEntity<EodStatusDTO> getEodStatus(@Parameter(description = "Account ID") @PathVariable Integer accountId, @Parameter(description = "Business date") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {

        LocalDate date = businessDate != null ? businessDate : LocalDate.now();

        // BEFORE: controller → eodService.getEodStatus() → repository
        // AFTER:  controller → repository (direct)
        EodStatusDTO status = eodRepository.getEodStatus(accountId, date);

        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(status);
    }

    /**
     * Get EOD history for an account.
     * Direct repository call - simple query, no business logic.
     */
    @GetMapping("/history/{accountId}")
    @Operation(summary = "Get EOD processing history")
    public ResponseEntity<List<EodStatusDTO>> getEodHistory(@Parameter(description = "Account ID") @PathVariable Integer accountId, @Parameter(description = "Number of days of history") @RequestParam(defaultValue = "30") int days) {

        // Direct repository access
        List<EodStatusDTO> history = eodRepository.getEodHistory(accountId, days);
        return ResponseEntity.ok(history);
    }

    /**
     * Get positions for an account on a business date.
     * Direct repository call.
     */
    @GetMapping("/positions/{accountId}")
    @Operation(summary = "Get positions for a business date")
    public ResponseEntity<List<PositionDTO>> getPositions(@Parameter(description = "Account ID") @PathVariable Integer accountId, @Parameter(description = "Business date") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {

        LocalDate date = businessDate != null ? businessDate : LocalDate.now();

        // Direct repository access
        List<PositionDTO> positions = positionRepository.getPositionsByDate(accountId, date);
        return ResponseEntity.ok(positions);
    }

    /**
     * Get batch information for an account.
     */
    @GetMapping("/batch/{accountId}")
    @Operation(summary = "Get current batch info")
    public ResponseEntity<?> getBatchInfo(@Parameter(description = "Account ID") @PathVariable Integer accountId) {

        // Direct repository access
        int currentBatch = positionRepository.getNextBatchId(accountId) - 1;

        return ResponseEntity.ok(Map.of("accountId", accountId, "currentBatchId", Math.max(0, currentBatch), "nextBatchId", currentBatch + 1));
    }

    /**
     * Check if EOD has run for today.
     */
    @GetMapping("/ran-today/{accountId}")
    @Operation(summary = "Check if EOD ran today")
    public ResponseEntity<?> hasEodRanToday(@Parameter(description = "Account ID") @PathVariable Integer accountId) {

        EodStatusDTO status = eodRepository.getEodStatus(accountId, LocalDate.now());
        boolean ranToday = status != null && "COMPLETED".equals(status.status());

        return ResponseEntity.ok(Map.of("accountId", accountId, "date", LocalDate.now(), "eodCompleted", ranToday));
    }
}