package com.vyshali.positionloader.controller;

import com.vyshali.fxanalyzer.common.dto.ApiResponse;
import com.vyshali.fxanalyzer.common.entity.Snapshot;
import com.vyshali.fxanalyzer.common.repository.SnapshotRepository;
import com.vyshali.fxanalyzer.positionloader.dto.MspmPositionMessage;
import com.vyshali.fxanalyzer.positionloader.service.PositionLoadService;
import com.vyshali.fxanalyzer.positionloader.service.SnapshotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for Position Loader service.
 * Provides health check, metrics, and manual operation endpoints.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/position-loader")
@RequiredArgsConstructor
@Tag(name = "Position Loader", description = "Position loading and snapshot management")
public class PositionLoaderController {

    private final PositionLoadService positionLoadService;
    private final SnapshotService snapshotService;
    private final SnapshotRepository snapshotRepository;

    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    @Operation(summary = "Health check")
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "position-loader");
        health.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(ApiResponse.success(health));
    }

    /**
     * Get current metrics.
     */
    @GetMapping("/metrics")
    @Operation(summary = "Get loader metrics")
    public ResponseEntity<ApiResponse<PositionLoadService.LoaderMetrics>> getMetrics() {
        return ResponseEntity.ok(ApiResponse.success(positionLoadService.getMetrics()));
    }

    /**
     * Get snapshots for a date.
     */
    @GetMapping("/snapshots")
    @Operation(summary = "Get snapshots by date")
    public ResponseEntity<ApiResponse<List<Snapshot>>> getSnapshots(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "ACTIVE") String status) {
        
        List<Snapshot> snapshots = snapshotRepository.findBySnapshotDateAndStatus(date, status);
        return ResponseEntity.ok(ApiResponse.success(snapshots));
    }

    /**
     * Get snapshot count for today.
     */
    @GetMapping("/snapshots/count/today")
    @Operation(summary = "Get today's snapshot count")
    public ResponseEntity<ApiResponse<Long>> getTodaySnapshotCount() {
        long count = snapshotRepository.countActiveByDate(LocalDate.now());
        return ResponseEntity.ok(ApiResponse.success(count));
    }

    /**
     * Manual position upload (for testing or manual loads).
     */
    @PostMapping("/load")
    @Operation(summary = "Manually load positions")
    public ResponseEntity<ApiResponse<Map<String, Object>>> loadPositions(
            @RequestBody MspmPositionMessage message) {
        
        log.info("Manual position load request for account {}", message.getAccountNumber());
        
        positionLoadService.processPositionMessage(message);
        
        Map<String, Object> result = new HashMap<>();
        result.put("accountNumber", message.getAccountNumber());
        result.put("positionCount", message.getPositions() != null ? message.getPositions().size() : 0);
        result.put("snapshotDate", message.getSnapshotDate());
        result.put("status", "LOADED");
        
        return ResponseEntity.ok(ApiResponse.success(result, "Positions loaded successfully"));
    }

    /**
     * Deactivate a snapshot.
     */
    @PostMapping("/snapshots/{snapshotId}/deactivate")
    @Operation(summary = "Deactivate a snapshot")
    public ResponseEntity<ApiResponse<Void>> deactivateSnapshot(@PathVariable Long snapshotId) {
        snapshotService.deactivateSnapshot(snapshotId);
        return ResponseEntity.ok(ApiResponse.success(null, "Snapshot deactivated"));
    }

    /**
     * Clean up old snapshots (manual trigger).
     */
    @PostMapping("/cleanup")
    @Operation(summary = "Clean up old snapshots")
    public ResponseEntity<ApiResponse<Map<String, Object>>> cleanupOldSnapshots(
            @RequestParam(defaultValue = "7") int retentionDays) {
        
        int deleted = snapshotService.deleteOldSnapshots(retentionDays);
        
        Map<String, Object> result = new HashMap<>();
        result.put("retentionDays", retentionDays);
        result.put("snapshotsDeleted", deleted);
        
        return ResponseEntity.ok(ApiResponse.success(result, "Cleanup completed"));
    }
}
