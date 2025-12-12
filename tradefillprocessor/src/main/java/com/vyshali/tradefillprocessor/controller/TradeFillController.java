package com.vyshali.tradefillprocessor.controller;

import com.vyshali.fxanalyzer.common.dto.ApiResponse;
import com.vyshali.fxanalyzer.common.entity.TradeExecution;
import com.vyshali.fxanalyzer.common.repository.TradeExecutionRepository;
import com.vyshali.fxanalyzer.tradefillprocessor.dto.FxMatrixFillMessage;
import com.vyshali.fxanalyzer.tradefillprocessor.service.TradeExecutionService;
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
 * REST controller for Trade Fill Processor service.
 * Provides health check, metrics, and trade execution queries.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/trade-fills")
@RequiredArgsConstructor
@Tag(name = "Trade Fill Processor", description = "Trade execution tracking and audit")
public class TradeFillController {

    private final TradeExecutionService tradeExecutionService;
    private final TradeExecutionRepository tradeExecutionRepository;

    // ==================== Health & Status ====================

    @GetMapping("/health")
    @Operation(summary = "Health check")
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "trade-fill-processor");
        health.put("stats", tradeExecutionService.getStats());
        return ResponseEntity.ok(ApiResponse.success(health));
    }

    @GetMapping("/stats")
    @Operation(summary = "Get execution statistics")
    public ResponseEntity<ApiResponse<TradeExecutionService.ExecutionStats>> getStats() {
        return ResponseEntity.ok(ApiResponse.success(tradeExecutionService.getStats()));
    }

    // ==================== Trade Execution Queries ====================

    @GetMapping("/executions")
    @Operation(summary = "Get trade executions by date")
    public ResponseEntity<ApiResponse<List<TradeExecution>>> getExecutions(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        List<TradeExecution> executions = tradeExecutionRepository.findByExecutionDate(date);
        return ResponseEntity.ok(ApiResponse.success(executions));
    }

    @GetMapping("/executions/pending")
    @Operation(summary = "Get pending trade executions")
    public ResponseEntity<ApiResponse<List<TradeExecution>>> getPendingExecutions() {
        List<TradeExecution> pending = tradeExecutionRepository.findPendingExecutions();
        return ResponseEntity.ok(ApiResponse.success(pending));
    }

    @GetMapping("/executions/failed")
    @Operation(summary = "Get failed trade executions")
    public ResponseEntity<ApiResponse<List<TradeExecution>>> getFailedExecutions() {
        List<TradeExecution> failed = tradeExecutionRepository.findFailedExecutions();
        return ResponseEntity.ok(ApiResponse.success(failed));
    }

    @GetMapping("/executions/stale")
    @Operation(summary = "Get stale pending trades")
    public ResponseEntity<ApiResponse<List<TradeExecution>>> getStalePending(
            @RequestParam(defaultValue = "5") int timeoutMinutes) {
        List<TradeExecution> stale = tradeExecutionService.getStalePendingTrades(timeoutMinutes);
        return ResponseEntity.ok(ApiResponse.success(stale));
    }

    @GetMapping("/executions/{executionRef}")
    @Operation(summary = "Get trade execution by reference")
    public ResponseEntity<ApiResponse<TradeExecution>> getExecution(@PathVariable String executionRef) {
        return tradeExecutionRepository.findByFxMatrixRef(executionRef)
                .map(e -> ResponseEntity.ok(ApiResponse.success(e)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/executions/account/{accountNumber}")
    @Operation(summary = "Get trade executions by account")
    public ResponseEntity<ApiResponse<List<TradeExecution>>> getExecutionsByAccount(
            @PathVariable String accountNumber,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        List<TradeExecution> executions = tradeExecutionRepository
                .findByAccountAndDate(accountNumber, date);
        return ResponseEntity.ok(ApiResponse.success(executions));
    }

    // ==================== Manual Operations ====================

    @PostMapping("/process")
    @Operation(summary = "Manually process a trade fill (for testing)")
    public ResponseEntity<ApiResponse<Void>> processFill(@RequestBody FxMatrixFillMessage fill) {
        log.info("Manual fill processing request: {}", fill.getExecutionRef());
        tradeExecutionService.processFill(fill);
        return ResponseEntity.ok(ApiResponse.success(null, "Fill processed successfully"));
    }

    @PostMapping("/mark-stale-failed")
    @Operation(summary = "Mark stale pending trades as failed")
    public ResponseEntity<ApiResponse<Map<String, Object>>> markStaleFailed(
            @RequestParam(defaultValue = "5") int timeoutMinutes) {
        int count = tradeExecutionService.markStalePendingTradesAsFailed(timeoutMinutes);
        
        Map<String, Object> result = new HashMap<>();
        result.put("timeoutMinutes", timeoutMinutes);
        result.put("tradesMarkedFailed", count);
        
        return ResponseEntity.ok(ApiResponse.success(result, "Stale trades processed"));
    }
}
