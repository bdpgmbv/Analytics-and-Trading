package com.vyshali.positionloader.controller;

/*
 * 12/09/2025 - 3:48 PM
 * @author Vyshali Prabananth Lal
 */

/*
 * CRITICAL FIX #6: EOD Progress REST Controller
 *
 * Provides REST API for Ops dashboard
 *
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.positionloader.service.EodProgressTracker;
import com.vyshali.positionloader.service.ParallelEodService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ops/eod")
public class EodProgressController {

    private static final Logger log = LoggerFactory.getLogger(EodProgressController.class);

    private final ParallelEodService parallelEodService;
    private final EodProgressTracker progressTracker;

    public EodProgressController(ParallelEodService parallelEodService, EodProgressTracker progressTracker) {
        this.parallelEodService = parallelEodService;
        this.progressTracker = progressTracker;
    }

    @GetMapping("/progress")
    public ResponseEntity<ProgressResponse> getProgress(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        LocalDate businessDate = date != null ? date : LocalDate.now();
        ParallelEodService.EodProgress progress = parallelEodService.getProgress(businessDate);

        if (progress == null) {
            return ResponseEntity.ok(new ProgressResponse(businessDate, "NOT_STARTED", 0, 0, 0, 0, 0, 0.0, null, null));
        }

        String status = determineStatus(progress);

        return ResponseEntity.ok(new ProgressResponse(progress.businessDate(), status, progress.total(), progress.completed(), progress.failed(), progress.inProgress(), progress.pending(), progress.percentComplete(), progress.startTime() != null ? progress.startTime().toString() : null, progress.estimatedCompletion()));
    }

    @GetMapping("/failures")
    public ResponseEntity<FailuresResponse> getFailures(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        LocalDate businessDate = date != null ? date : LocalDate.now();
        List<ParallelEodService.FailedAccount> failures = parallelEodService.getFailedAccounts(businessDate);

        return ResponseEntity.ok(new FailuresResponse(businessDate, failures.size(), failures));
    }

    @GetMapping("/pending")
    public ResponseEntity<PendingResponse> getPending(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        LocalDate businessDate = date != null ? date : LocalDate.now();
        List<Integer> pending = progressTracker.getPendingAccounts(businessDate);

        return ResponseEntity.ok(new PendingResponse(businessDate, pending.size(), pending));
    }

    @GetMapping("/by-client")
    public ResponseEntity<Map<String, EodProgressTracker.ClientProgress>> getByClient(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        LocalDate businessDate = date != null ? date : LocalDate.now();
        return ResponseEntity.ok(progressTracker.getProgressByClient(businessDate));
    }

    @PostMapping("/retry")
    public ResponseEntity<RetryResponse> retryAccounts(@RequestBody RetryRequest request) {
        log.info("Manual retry requested for {} accounts on {}", request.accountIds().size(), request.date());

        LocalDate businessDate = request.date() != null ? request.date() : LocalDate.now();

        List<ParallelEodService.AccountResult> results = parallelEodService.retryAccounts(request.accountIds(), businessDate);

        int success = (int) results.stream().filter(ParallelEodService.AccountResult::success).count();
        int failed = results.size() - success;

        return ResponseEntity.ok(new RetryResponse(businessDate, request.accountIds().size(), success, failed, results));
    }

    @PostMapping("/retry-all-failed")
    public ResponseEntity<RetryResponse> retryAllFailed(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        LocalDate businessDate = date != null ? date : LocalDate.now();

        List<ParallelEodService.FailedAccount> failures = parallelEodService.getFailedAccounts(businessDate);

        if (failures.isEmpty()) {
            return ResponseEntity.ok(new RetryResponse(businessDate, 0, 0, 0, List.of()));
        }

        List<Integer> accountIds = failures.stream().map(ParallelEodService.FailedAccount::accountId).toList();

        log.info("Retrying all {} failed accounts for {}", accountIds.size(), businessDate);

        List<ParallelEodService.AccountResult> results = parallelEodService.retryAccounts(accountIds, businessDate);

        int success = (int) results.stream().filter(ParallelEodService.AccountResult::success).count();
        int failed = results.size() - success;

        return ResponseEntity.ok(new RetryResponse(businessDate, accountIds.size(), success, failed, results));
    }

    @GetMapping("/health")
    public ResponseEntity<HealthResponse> getHealth() {
        LocalDate today = LocalDate.now();
        ParallelEodService.EodProgress progress = parallelEodService.getProgress(today);

        String status = "UNKNOWN";
        String message = "No EOD run today";

        if (progress != null) {
            if (progress.failed() > progress.total() * 0.1) {
                status = "UNHEALTHY";
                message = String.format("High failure rate: %d/%d failed", progress.failed(), progress.total());
            } else if (progress.percentComplete() >= 100) {
                status = "HEALTHY";
                message = "EOD complete";
            } else {
                status = "IN_PROGRESS";
                message = String.format("%.1f%% complete", progress.percentComplete());
            }
        }

        return ResponseEntity.ok(new HealthResponse(today, status, message, progress));
    }

    private String determineStatus(ParallelEodService.EodProgress progress) {
        if (progress.percentComplete() >= 100) {
            return progress.failed() > 0 ? "COMPLETED_WITH_ERRORS" : "COMPLETED";
        } else if (progress.inProgress() > 0) {
            return "IN_PROGRESS";
        } else if (progress.pending() > 0) {
            return "PENDING";
        }
        return "UNKNOWN";
    }

    // DTOs
    public record ProgressResponse(LocalDate businessDate, String status, int totalAccounts, int completed, int failed,
                                   int inProgress, int pending, double percentComplete, String startTime,
                                   String estimatedCompletion) {
    }

    public record FailuresResponse(LocalDate businessDate, int count, List<ParallelEodService.FailedAccount> failures) {
    }

    public record PendingResponse(LocalDate businessDate, int count, List<Integer> accountIds) {
    }

    public record RetryRequest(LocalDate date, List<Integer> accountIds) {
    }

    public record RetryResponse(LocalDate businessDate, int attempted, int success, int failed,
                                List<ParallelEodService.AccountResult> results) {
    }

    public record HealthResponse(LocalDate date, String status, String message,
                                 ParallelEodService.EodProgress progress) {
    }
}
