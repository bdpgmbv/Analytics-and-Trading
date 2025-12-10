package com.vyshali.positionloader.controller;

/*
 * 12/10/2025 - 1:01 PM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.positionloader.dto.EodProgress;
import com.vyshali.positionloader.service.EodService;
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

    public record RetryRequest(LocalDate date, List<Integer> accountIds) {
    }
}