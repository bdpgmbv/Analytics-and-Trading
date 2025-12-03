package com.vyshali.hedgeservice.controller;

/*
 * 12/03/2025 - 12:12 PM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.hedgeservice.dto.*;
import com.vyshali.hedgeservice.service.HedgeAnalyticsService;
import com.vyshali.hedgeservice.service.HedgeExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/hedge")
@RequiredArgsConstructor
public class HedgeController {

    private final HedgeAnalyticsService service;

    private final HedgeExecutionService executionService;

    // --- READ OPERATIONS (Tabs 1, 2, 3, 4, 5) ---

    @GetMapping("/transactions")
    public ResponseEntity<List<TransactionViewDTO>> getTransactions(@RequestParam Integer accountId) {
        return ResponseEntity.ok(service.getTransactions(accountId));
    }

    @GetMapping("/positions/upload-view")
    public ResponseEntity<List<PositionUploadDTO>> getPositionUploadView(@RequestParam Integer accountId) {
        return ResponseEntity.ok(service.getPositionUploadView(accountId));
    }

    @GetMapping("/positions/hedge-view")
    public ResponseEntity<List<HedgePositionDTO>> getHedgePositions(@RequestParam Integer accountId) {
        return ResponseEntity.ok(service.getHedgePositions(accountId));
    }

    @GetMapping("/alerts/forward-maturity")
    public ResponseEntity<List<ForwardMaturityDTO>> getForwardMaturityAlerts(@RequestParam Integer accountId) {
        return ResponseEntity.ok(service.getForwardMaturityAlerts(accountId));
    }

    @GetMapping("/cash-management")
    public ResponseEntity<List<CashManagementDTO>> getCashManagement(@RequestParam Integer fundId) {
        return ResponseEntity.ok(service.getCashManagement(fundId));
    }

    // --- WRITE OPERATIONS (Tab 2 Action) ---

    @PostMapping("/positions/upload")
    public ResponseEntity<String> uploadManualPosition(@RequestBody ManualPositionInputDTO input) {
        service.saveManualPosition(input);
        return ResponseEntity.ok("Manual Position Saved Successfully");
    }

    // THE "SEND" BUTTON
    @PostMapping("/execute")
    public ResponseEntity<String> sendHedgeOrder(@RequestBody HedgeExecutionRequestDTO order) {
        String orderId = executionService.executeHedge(order);
        return ResponseEntity.ok("Hedge Order Sent to FX Matrix. ID: " + orderId);
    }
}