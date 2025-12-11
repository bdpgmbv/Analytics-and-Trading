package com.vyshali.positionloader.controller;

/*
 * 12/11/2025 - 11:48 AM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.positionloader.dto.Dto;
import com.vyshali.positionloader.repository.DataRepository;
import com.vyshali.positionloader.service.PositionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Single controller for all Position Loader endpoints.
 * Replaces: EodController, UploadController, OpsController
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Position Loader", description = "EOD and Intraday position operations")
public class PositionController {

    private final PositionService service;
    private final DataRepository repo;

    // ═══════════════════════════════════════════════════════════════════════════
    // EOD OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    @PostMapping("/eod/trigger/{accountId}")
    @Operation(summary = "Trigger EOD for an account")
    public ResponseEntity<Map<String, Object>> triggerEod(@PathVariable Integer accountId, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        LocalDate businessDate = date != null ? date : LocalDate.now();
        log.info("EOD trigger: account={}, date={}", accountId, businessDate);

        try {
            service.processEod(accountId);
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "accountId", accountId, "businessDate", businessDate));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("status", "FAILED", "accountId", accountId, "error", e.getMessage()));
        }
    }

    @GetMapping("/eod/status/{accountId}")
    @Operation(summary = "Get EOD status")
    public ResponseEntity<Dto.EodStatus> getEodStatus(@PathVariable Integer accountId, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        Dto.EodStatus status = repo.getEodStatus(accountId, date != null ? date : LocalDate.now());
        return status != null ? ResponseEntity.ok(status) : ResponseEntity.notFound().build();
    }

    @GetMapping("/eod/history/{accountId}")
    @Operation(summary = "Get EOD history")
    public ResponseEntity<List<Dto.EodStatus>> getEodHistory(@PathVariable Integer accountId, @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(repo.getEodHistory(accountId, days));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // POSITION QUERIES
    // ═══════════════════════════════════════════════════════════════════════════

    @GetMapping("/positions/{accountId}")
    @Operation(summary = "Get positions for account")
    public ResponseEntity<List<Dto.Position>> getPositions(@PathVariable Integer accountId, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(repo.getPositionsByDate(accountId, date != null ? date : LocalDate.now()));
    }

    @GetMapping("/positions/{accountId}/{productId}/as-of")
    @Operation(summary = "Get position quantity as of date")
    public ResponseEntity<BigDecimal> getPositionAsOf(@PathVariable Integer accountId, @PathVariable Integer productId, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(repo.getQuantityAsOf(accountId, productId, date));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UPLOAD
    // ═══════════════════════════════════════════════════════════════════════════

    @PostMapping("/positions/upload/{accountId}")
    @Operation(summary = "Upload positions")
    public ResponseEntity<Map<String, Object>> uploadPositions(@PathVariable Integer accountId, @RequestBody List<Dto.Position> positions) {

        if (positions.size() > 10000) {
            return ResponseEntity.badRequest().body(Map.of("error", "Max 10,000 positions per upload"));
        }

        int processed = service.processUpload(accountId, positions);

        return ResponseEntity.ok(Map.of("accountId", accountId, "received", positions.size(), "processed", processed));
    }

    @PostMapping("/intraday")
    @Operation(summary = "Process intraday update")
    public ResponseEntity<Map<String, String>> processIntraday(@RequestBody Dto.AccountSnapshot snapshot) {
        service.processIntraday(snapshot);
        return ResponseEntity.ok(Map.of("status", "SUCCESS"));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HEALTH
    // ═══════════════════════════════════════════════════════════════════════════

    @GetMapping("/health")
    @Operation(summary = "Health check")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "positionloader"));
    }
}
