package com.vyshali.positionloader.controller;

import com.vyshali.positionloader.dto.Dto;
import com.vyshali.positionloader.repository.DataRepository;
import com.vyshali.positionloader.service.PositionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * REST API for Position Loader operations.
 *
 * Phase 2 Addition: /api/v1/ops/rollback endpoint
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Position Loader", description = "EOD and position management")
public class PositionController {

    private final PositionService service;
    private final DataRepository repo;

    // ═══════════════════════════════════════════════════════════════════════════
    // EOD OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    @Operation(summary = "Trigger EOD processing for an account")
    @PostMapping("/api/v1/eod/{accountId}")
    public ResponseEntity<Map<String, Object>> triggerEod(@PathVariable Integer accountId) {
        log.info("Manual EOD trigger for account {}", accountId);
        service.processEod(accountId);
        return ResponseEntity.ok(Map.of(
                "status", "completed",
                "accountId", accountId,
                "businessDate", LocalDate.now()
        ));
    }

    @Operation(summary = "Get EOD status for an account")
    @GetMapping("/api/v1/eod/{accountId}/status")
    public ResponseEntity<Dto.EodStatus> getEodStatus(
            @PathVariable Integer accountId,
            @RequestParam(required = false) LocalDate date) {
        LocalDate businessDate = date != null ? date : LocalDate.now();
        Dto.EodStatus status = repo.getEodStatus(accountId, businessDate);
        return status != null ? ResponseEntity.ok(status) : ResponseEntity.notFound().build();
    }

    @Operation(summary = "Get EOD history for an account")
    @GetMapping("/api/v1/eod/{accountId}/history")
    public ResponseEntity<List<Dto.EodStatus>> getEodHistory(
            @PathVariable Integer accountId,
            @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(repo.getEodHistory(accountId, days));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // POSITION QUERIES
    // ═══════════════════════════════════════════════════════════════════════════

    @Operation(summary = "Get positions for an account on a date")
    @GetMapping("/api/v1/accounts/{accountId}/positions")
    public ResponseEntity<List<Dto.Position>> getPositions(
            @PathVariable Integer accountId,
            @RequestParam(required = false) LocalDate date) {
        LocalDate businessDate = date != null ? date : LocalDate.now();
        return ResponseEntity.ok(repo.getPositionsByDate(accountId, businessDate));
    }

    @Operation(summary = "Get ACTIVE batch positions only (Phase 2)")
    @GetMapping("/api/v1/accounts/{accountId}/positions/active")
    public ResponseEntity<List<Dto.Position>> getActivePositions(
            @PathVariable Integer accountId,
            @RequestParam(required = false) LocalDate date) {
        LocalDate businessDate = date != null ? date : LocalDate.now();
        return ResponseEntity.ok(repo.getActivePositions(accountId, businessDate));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UPLOAD
    // ═══════════════════════════════════════════════════════════════════════════

    @Operation(summary = "Upload positions manually")
    @PostMapping("/api/v1/accounts/{accountId}/positions")
    public ResponseEntity<Map<String, Object>> uploadPositions(
            @PathVariable Integer accountId,
            @RequestBody List<Dto.Position> positions) {
        int count = service.processUpload(accountId, positions);
        return ResponseEntity.ok(Map.of(
                "status", "uploaded",
                "accountId", accountId,
                "count", count
        ));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PHASE 2: OPS OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    @Operation(summary = "Rollback EOD to previous batch (Phase 2)")
    @PostMapping("/api/v1/ops/rollback/{accountId}")
    public ResponseEntity<Map<String, Object>> rollbackEod(
            @PathVariable Integer accountId,
            @RequestParam(required = false) LocalDate date) {
        LocalDate businessDate = date != null ? date : LocalDate.now();
        log.warn("OPS: Rollback requested for account {} on {}", accountId, businessDate);

        boolean success = service.rollbackEod(accountId, businessDate);

        if (success) {
            return ResponseEntity.ok(Map.of(
                    "status", "rolled_back",
                    "accountId", accountId,
                    "businessDate", businessDate
            ));
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "failed",
                    "accountId", accountId,
                    "message", "No previous batch available to rollback to"
            ));
        }
    }

    @Operation(summary = "Get DLQ status")
    @GetMapping("/api/v1/ops/dlq")
    public ResponseEntity<Map<String, Object>> getDlqStatus() {
        int depth = repo.getDlqDepth();
        List<Map<String, Object>> messages = repo.getDlqMessages(20);
        return ResponseEntity.ok(Map.of(
                "depth", depth,
                "sample", messages
        ));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HEALTH (simple)
    // ═══════════════════════════════════════════════════════════════════════════

    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("pong");
    }
}