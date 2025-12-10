package com.vyshali.positionloader.controller;

/*
 * 12/10/2025 - 1:01 PM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.positionloader.dto.EodProgress;
import com.vyshali.positionloader.service.EodService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * EOD progress monitoring and retry endpoints.
 */
@RestController
@RequestMapping("/api/eod")
@RequiredArgsConstructor
@Tag(name = "EOD", description = "End-of-Day Processing")
public class EodController {

    private final EodService eodService;

    @GetMapping("/progress")
    public ResponseEntity<EodProgress.Status> getProgress(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        LocalDate businessDate = date != null ? date : LocalDate.now();
        EodProgress.Status status = eodService.getProgress(businessDate);

        if (status == null) {
            return ResponseEntity.ok(new EodProgress.Status(businessDate, 0, 0, 0, 0, 0, null, "NOT_STARTED"));
        }
        return ResponseEntity.ok(status);
    }

    @GetMapping("/failures")
    public ResponseEntity<List<EodProgress.FailedAccount>> getFailures(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        LocalDate businessDate = date != null ? date : LocalDate.now();
        return ResponseEntity.ok(eodService.getFailures(businessDate));
    }

    @GetMapping("/pending")
    public ResponseEntity<List<Integer>> getPending(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        LocalDate businessDate = date != null ? date : LocalDate.now();
        return ResponseEntity.ok(eodService.getPending(businessDate));
    }

    @PostMapping("/retry")
    public ResponseEntity<Map<String, Object>> retry(@RequestBody RetryRequest request) {

        LocalDate businessDate = request.date != null ? request.date : LocalDate.now();
        List<EodProgress.AccountResult> results = eodService.retry(request.accountIds, businessDate);

        int success = (int) results.stream().filter(EodProgress.AccountResult::success).count();

        return ResponseEntity.ok(Map.of("date", businessDate, "attempted", request.accountIds.size(), "success", success, "failed", request.accountIds.size() - success));
    }

    @PostMapping("/retry-all-failed")
    public ResponseEntity<Map<String, Object>> retryAllFailed(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        LocalDate businessDate = date != null ? date : LocalDate.now();
        List<EodProgress.FailedAccount> failures = eodService.getFailures(businessDate);

        if (failures.isEmpty()) {
            return ResponseEntity.ok(Map.of("message", "No failures to retry"));
        }

        List<Integer> failedIds = failures.stream().map(EodProgress.FailedAccount::accountId).toList();

        List<EodProgress.AccountResult> results = eodService.retry(failedIds, businessDate);
        int success = (int) results.stream().filter(EodProgress.AccountResult::success).count();

        return ResponseEntity.ok(Map.of("date", businessDate, "attempted", failedIds.size(), "success", success, "failed", failedIds.size() - success));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealth() {
        LocalDate today = LocalDate.now();
        EodProgress.Status status = eodService.getProgress(today);

        String health = "UNKNOWN";
        String message = "No EOD run today";

        if (status != null) {
            if (status.failed() > status.total() * 0.1) {
                health = "UNHEALTHY";
                message = String.format("High failure rate: %d/%d", status.failed(), status.total());
            } else if (status.percentComplete() >= 100) {
                health = "HEALTHY";
                message = "EOD complete";
            } else {
                health = "IN_PROGRESS";
                message = String.format("%.1f%% complete", status.percentComplete());
            }
        }

        return ResponseEntity.ok(Map.of("date", today, "health", health, "message", message));
    }

    // Request DTOs
    public record RetryRequest(LocalDate date, List<Integer> accountIds) {
    }
}
