package com.vyshali.positionloader.controller;

import com.vyshali.positionloader.config.AppConfig.LoaderConfig;
import com.vyshali.positionloader.dto.Dto;
import com.vyshali.positionloader.repository.DataRepository;
import com.vyshali.positionloader.service.BusinessDayService;
import com.vyshali.positionloader.service.PositionService;
import com.vyshali.positionloader.service.ReconciliationService;
import com.vyshali.positionloader.service.ReconciliationService.PositionDiffReport;
import com.vyshali.positionloader.service.ReconciliationService.ReconciliationReport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * REST API for Position Loader - Phase 1-4 Complete
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Position Loader", description = "EOD, positions, reconciliation, and ops management")
public class PositionController {

    private final PositionService service;
    private final DataRepository repo;
    private final ReconciliationService reconciliationService;
    private final BusinessDayService businessDayService;
    private final LoaderConfig config;

    // ═══════════════════════════════════════════════════════════════════════════
    // EOD OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    @Operation(summary = "Trigger EOD processing for an account")
    @PostMapping("/api/v1/eod/{accountId}")
    public ResponseEntity<Map<String, Object>> triggerEod(@PathVariable Integer accountId) {
        log.info("Manual EOD trigger for account {}", accountId);
        service.processEod(accountId);
        return ResponseEntity.ok(Map.of("status", "completed", "accountId", accountId, "businessDate", LocalDate.now()));
    }

    @Operation(summary = "Get EOD status for an account")
    @GetMapping("/api/v1/eod/{accountId}/status")
    public ResponseEntity<Dto.EodStatus> getEodStatus(@PathVariable Integer accountId, @RequestParam(required = false) LocalDate date) {
        LocalDate businessDate = date != null ? date : LocalDate.now();
        Dto.EodStatus status = repo.getEodStatus(accountId, businessDate);
        return status != null ? ResponseEntity.ok(status) : ResponseEntity.notFound().build();
    }

    @Operation(summary = "Get EOD history for an account")
    @GetMapping("/api/v1/eod/{accountId}/history")
    public ResponseEntity<List<Dto.EodStatus>> getEodHistory(@PathVariable Integer accountId, @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(repo.getEodHistory(accountId, days));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PHASE 4 #21: LATE ARRIVAL
    // ═══════════════════════════════════════════════════════════════════════════

    @Operation(summary = "Process late EOD data for a past date (Phase 4)")
    @PostMapping("/api/v1/eod/{accountId}/late")
    public ResponseEntity<Map<String, Object>> processLateEod(@PathVariable Integer accountId, @RequestParam LocalDate businessDate) {
        log.warn("Late EOD request for account {} date {}", accountId, businessDate);

        try {
            service.processLateEod(accountId, businessDate);
            return ResponseEntity.ok(Map.of("status", "completed", "accountId", accountId, "businessDate", businessDate, "lateArrival", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "rejected", "error", e.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // POSITION QUERIES
    // ═══════════════════════════════════════════════════════════════════════════

    @Operation(summary = "Get positions for an account on a date")
    @GetMapping("/api/v1/accounts/{accountId}/positions")
    public ResponseEntity<List<Dto.Position>> getPositions(@PathVariable Integer accountId, @RequestParam(required = false) LocalDate date) {
        LocalDate businessDate = date != null ? date : LocalDate.now();
        return ResponseEntity.ok(repo.getPositionsByDate(accountId, businessDate));
    }

    @Operation(summary = "Get ACTIVE batch positions only")
    @GetMapping("/api/v1/accounts/{accountId}/positions/active")
    public ResponseEntity<List<Dto.Position>> getActivePositions(@PathVariable Integer accountId, @RequestParam(required = false) LocalDate date) {
        LocalDate businessDate = date != null ? date : LocalDate.now();
        return ResponseEntity.ok(repo.getActivePositions(accountId, businessDate));
    }

    @Operation(summary = "Upload positions manually")
    @PostMapping("/api/v1/accounts/{accountId}/positions")
    public ResponseEntity<Map<String, Object>> uploadPositions(@PathVariable Integer accountId, @RequestBody List<Dto.Position> positions) {
        int count = service.processUpload(accountId, positions);
        return ResponseEntity.ok(Map.of("status", "uploaded", "accountId", accountId, "count", count));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RECONCILIATION (Phase 3)
    // ═══════════════════════════════════════════════════════════════════════════

    @Operation(summary = "Run reconciliation for an account")
    @GetMapping("/api/v1/reconciliation/{accountId}")
    public ResponseEntity<ReconciliationReport> reconcileAccount(@PathVariable Integer accountId, @RequestParam(required = false) LocalDate date) {
        LocalDate businessDate = date != null ? date : LocalDate.now();
        return ResponseEntity.ok(reconciliationService.reconcile(accountId, businessDate));
    }

    @Operation(summary = "Run reconciliation for all accounts")
    @PostMapping("/api/v1/reconciliation/batch")
    public ResponseEntity<Map<String, Object>> reconcileAllAccounts(@RequestParam(required = false) LocalDate date) {
        LocalDate businessDate = date != null ? date : LocalDate.now();
        List<ReconciliationReport> reports = reconciliationService.reconcileAllAccounts(businessDate);

        long ok = reports.stream().filter(r -> "OK".equals(r.status())).count();
        long warning = reports.stream().filter(r -> "WARNING".equals(r.status())).count();
        long critical = reports.stream().filter(r -> "CRITICAL".equals(r.status())).count();

        return ResponseEntity.ok(Map.of("date", businessDate, "totalAccounts", reports.size(), "ok", ok, "warning", warning, "critical", critical, "reports", reports));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PHASE 4 #19: POSITION DIFF
    // ═══════════════════════════════════════════════════════════════════════════

    @Operation(summary = "Get detailed position diff between two dates (Phase 4)")
    @GetMapping("/api/v1/accounts/{accountId}/positions/diff")
    public ResponseEntity<PositionDiffReport> getPositionDiff(@PathVariable Integer accountId, @RequestParam(required = false) LocalDate currentDate, @RequestParam(required = false) LocalDate previousDate) {

        LocalDate curr = currentDate != null ? currentDate : LocalDate.now();
        LocalDate prev = previousDate != null ? previousDate : curr.minusDays(1);

        return ResponseEntity.ok(reconciliationService.computePositionDiff(accountId, curr, prev));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PHASE 4 #20: MANUAL OVERRIDE (OPS)
    // ═══════════════════════════════════════════════════════════════════════════

    @Operation(summary = "Manually adjust a position (Phase 4)")
    @PutMapping("/api/v1/ops/positions/{accountId}/{productId}")
    public ResponseEntity<Map<String, Object>> adjustPosition(@PathVariable Integer accountId, @PathVariable Integer productId, @RequestParam BigDecimal quantity, @RequestParam(required = false) BigDecimal price, @RequestParam String reason, @RequestHeader(value = "X-Actor", defaultValue = "OPS") String actor) {

        log.warn("OPS: Position adjustment request from {} for account {} product {}", actor, accountId, productId);

        service.adjustPosition(accountId, productId, quantity, price, reason, actor);

        return ResponseEntity.ok(Map.of("status", "adjusted", "accountId", accountId, "productId", productId, "newQuantity", quantity, "actor", actor, "reason", reason));
    }

    @Operation(summary = "Reset EOD status to allow reprocessing (Phase 4)")
    @DeleteMapping("/api/v1/ops/eod/{accountId}/status")
    public ResponseEntity<Map<String, Object>> resetEodStatus(@PathVariable Integer accountId, @RequestParam(required = false) LocalDate date, @RequestHeader(value = "X-Actor", defaultValue = "OPS") String actor) {

        LocalDate businessDate = date != null ? date : LocalDate.now();
        log.warn("OPS: Resetting EOD status for account {} on {} by {}", accountId, businessDate, actor);

        service.resetEodStatus(accountId, businessDate, actor);

        return ResponseEntity.ok(Map.of("status", "reset", "accountId", accountId, "businessDate", businessDate, "actor", actor));
    }

    @Operation(summary = "Rollback EOD to previous batch")
    @PostMapping("/api/v1/ops/rollback/{accountId}")
    public ResponseEntity<Map<String, Object>> rollbackEod(@PathVariable Integer accountId, @RequestParam(required = false) LocalDate date) {
        LocalDate businessDate = date != null ? date : LocalDate.now();
        boolean success = service.rollbackEod(accountId, businessDate);

        if (success) {
            return ResponseEntity.ok(Map.of("status", "rolled_back", "accountId", accountId, "businessDate", businessDate));
        } else {
            return ResponseEntity.badRequest().body(Map.of("status", "failed", "message", "No previous batch available"));
        }
    }

    @Operation(summary = "Get DLQ status")
    @GetMapping("/api/v1/ops/dlq")
    public ResponseEntity<Map<String, Object>> getDlqStatus() {
        return ResponseEntity.ok(Map.of("depth", repo.getDlqDepth(), "sample", repo.getDlqMessages(20)));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PHASE 4 #17: BUSINESS DAY INFO
    // ═══════════════════════════════════════════════════════════════════════════

    @Operation(summary = "Check if a date is a business day (Phase 4)")
    @GetMapping("/api/v1/calendar/is-business-day")
    public ResponseEntity<Map<String, Object>> isBusinessDay(@RequestParam(required = false) LocalDate date) {
        LocalDate checkDate = date != null ? date : LocalDate.now();
        boolean isBusinessDay = businessDayService.isBusinessDay(checkDate);

        return ResponseEntity.ok(Map.of("date", checkDate, "isBusinessDay", isBusinessDay, "previousBusinessDay", businessDayService.getPreviousBusinessDay(checkDate), "nextBusinessDay", businessDayService.getNextBusinessDay(checkDate)));
    }

    @Operation(summary = "Add a holiday (Phase 4)")
    @PostMapping("/api/v1/calendar/holidays")
    public ResponseEntity<Map<String, Object>> addHoliday(@RequestParam LocalDate date, @RequestParam String name, @RequestParam(defaultValue = "US") String country) {
        businessDayService.addHoliday(date, name, country);
        return ResponseEntity.ok(Map.of("status", "added", "date", date, "name", name));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PHASE 4 #18: FEATURE FLAGS
    // ═══════════════════════════════════════════════════════════════════════════

    @Operation(summary = "Get current feature flag status (Phase 4)")
    @GetMapping("/api/v1/ops/features")
    public ResponseEntity<Map<String, Object>> getFeatureFlags() {
        return ResponseEntity.ok(Map.of("eodProcessingEnabled", config.features().eodProcessingEnabled(), "intradayProcessingEnabled", config.features().intradayProcessingEnabled(), "validationEnabled", config.features().validationEnabled(), "duplicateDetectionEnabled", config.features().duplicateDetectionEnabled(), "reconciliationEnabled", config.features().reconciliationEnabled(), "disabledAccounts", config.features().disabledAccounts(), "pilotAccounts", config.features().pilotAccounts()));
    }

    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("pong");
    }
}