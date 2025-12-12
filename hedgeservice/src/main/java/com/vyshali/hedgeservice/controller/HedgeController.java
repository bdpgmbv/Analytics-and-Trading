package com.vyshali.hedgeservice.controller;

import com.fxanalyzer.hedgeservice.dto.*;
import com.fxanalyzer.hedgeservice.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Hedge Service", description = "Main API for FX Analyzer - 6 UI Tabs")
public class HedgeController {
    
    private final SecurityExposureService securityExposureService;
    private final TransactionService transactionService;
    private final ForwardMaturityAlertService forwardMaturityAlertService;
    private final CashManagementService cashManagementService;
    private final ShareClassService shareClassService;
    private final PositionUploadService positionUploadService;
    
    // ========================================
    // Tab 1: Security Exposure
    // ========================================
    
    @GetMapping("/exposures/{portfolioId}")
    @Operation(summary = "Tab 1: Get Security Exposure", 
               description = "Get real-time currency exposures and P/L for a portfolio")
    public ResponseEntity<SecurityExposureDto> getSecurityExposure(
        @Parameter(description = "Portfolio ID") 
        @PathVariable Integer portfolioId,
        
        @Parameter(description = "As of date (default: today)") 
        @RequestParam(required = false) 
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) 
        LocalDate asOfDate
    ) {
        log.info("GET /api/v1/exposures/{} - asOfDate: {}", portfolioId, asOfDate);
        
        LocalDate effectiveDate = asOfDate != null ? asOfDate : LocalDate.now();
        SecurityExposureDto exposure = securityExposureService.getSecurityExposure(portfolioId, effectiveDate);
        
        return ResponseEntity.ok(exposure);
    }
    
    @GetMapping("/exposures/{portfolioId}/currency/{currency}")
    @Operation(summary = "Get exposure for specific currency")
    public ResponseEntity<SecurityExposureDto.CurrencyExposure> getCurrencyExposure(
        @PathVariable Integer portfolioId,
        @PathVariable String currency,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate
    ) {
        LocalDate effectiveDate = asOfDate != null ? asOfDate : LocalDate.now();
        SecurityExposureDto exposure = securityExposureService.getSecurityExposure(portfolioId, effectiveDate);
        
        return exposure.currencyExposures().stream()
            .filter(ce -> ce.currency().equals(currency))
            .findFirst()
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
    
    // ========================================
    // Tab 2: Transactions
    // ========================================
    
    @GetMapping("/transactions/{portfolioId}")
    @Operation(summary = "Tab 2: Get Transactions", 
               description = "Get current day transactions for a portfolio")
    public ResponseEntity<TransactionDto.TransactionSummary> getTransactions(
        @Parameter(description = "Portfolio ID") 
        @PathVariable Integer portfolioId,
        
        @Parameter(description = "Transaction date (default: today)") 
        @RequestParam(required = false) 
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) 
        LocalDate transactionDate
    ) {
        log.info("GET /api/v1/transactions/{} - date: {}", portfolioId, transactionDate);
        
        LocalDate effectiveDate = transactionDate != null ? transactionDate : LocalDate.now();
        TransactionDto.TransactionSummary summary = transactionService.getCurrentDayTransactions(
            portfolioId, 
            effectiveDate
        );
        
        return ResponseEntity.ok(summary);
    }
    
    @GetMapping("/transactions/{portfolioId}/pending")
    @Operation(summary = "Get pending transactions")
    public ResponseEntity<List<TransactionDto>> getPendingTransactions(
        @PathVariable Integer portfolioId
    ) {
        List<TransactionDto> transactions = transactionService.getPendingTransactions(portfolioId);
        return ResponseEntity.ok(transactions);
    }
    
    @GetMapping("/transactions/detail/{transactionId}")
    @Operation(summary = "Get transaction by ID")
    public ResponseEntity<TransactionDto> getTransactionById(
        @PathVariable Long transactionId
    ) {
        TransactionDto transaction = transactionService.getTransactionById(transactionId);
        return ResponseEntity.ok(transaction);
    }
    
    // ========================================
    // Tab 3: Share Class
    // ========================================
    
    @GetMapping("/share-classes/{fundId}")
    @Operation(summary = "Tab 3: Get Share Classes", 
               description = "Get share class currency hedging information")
    public ResponseEntity<ShareClassDto> getShareClasses(
        @Parameter(description = "Fund ID") 
        @PathVariable Integer fundId,
        
        @Parameter(description = "As of date (default: today)") 
        @RequestParam(required = false) 
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) 
        LocalDate asOfDate
    ) {
        log.info("GET /api/v1/share-classes/{} - asOfDate: {}", fundId, asOfDate);
        
        LocalDate effectiveDate = asOfDate != null ? asOfDate : LocalDate.now();
        ShareClassDto shareClasses = shareClassService.getShareClasses(fundId, effectiveDate);
        
        return ResponseEntity.ok(shareClasses);
    }
    
    @GetMapping("/share-classes/{fundId}/class/{shareClassId}")
    @Operation(summary = "Get specific share class")
    public ResponseEntity<ShareClassDto.ShareClass> getShareClass(
        @PathVariable Integer fundId,
        @PathVariable Integer shareClassId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate
    ) {
        LocalDate effectiveDate = asOfDate != null ? asOfDate : LocalDate.now();
        ShareClassDto shareClasses = shareClassService.getShareClasses(fundId, effectiveDate);
        
        return shareClasses.shareClasses().stream()
            .filter(sc -> sc.shareClassId().equals(shareClassId))
            .findFirst()
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
    
    // ========================================
    // Tab 4: Cash Management
    // ========================================
    
    @GetMapping("/cash-management/{portfolioId}")
    @Operation(summary = "Tab 4: Get Cash Management", 
               description = "Get currency cash balances and cash flows")
    public ResponseEntity<CashManagementDto> getCashManagement(
        @Parameter(description = "Portfolio ID") 
        @PathVariable Integer portfolioId,
        
        @Parameter(description = "As of date (default: today)") 
        @RequestParam(required = false) 
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) 
        LocalDate asOfDate
    ) {
        log.info("GET /api/v1/cash-management/{} - asOfDate: {}", portfolioId, asOfDate);
        
        LocalDate effectiveDate = asOfDate != null ? asOfDate : LocalDate.now();
        CashManagementDto cashManagement = cashManagementService.getCashManagement(portfolioId, effectiveDate);
        
        return ResponseEntity.ok(cashManagement);
    }
    
    @GetMapping("/cash-management/{portfolioId}/overdrafts")
    @Operation(summary = "Get overdraft cash balances")
    public ResponseEntity<List<CashManagementDto.CashBalance>> getOverdrafts(
        @PathVariable Integer portfolioId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate
    ) {
        LocalDate effectiveDate = asOfDate != null ? asOfDate : LocalDate.now();
        List<CashManagementDto.CashBalance> overdrafts = cashManagementService.getOverdrafts(
            portfolioId, 
            effectiveDate
        );
        
        return ResponseEntity.ok(overdrafts);
    }
    
    // ========================================
    // Tab 5: Forward Maturity Alerts
    // ========================================
    
    @GetMapping("/forward-alerts/{portfolioId}")
    @Operation(summary = "Tab 5: Get Forward Maturity Alerts", 
               description = "Get alerts for maturing forward contracts")
    public ResponseEntity<ForwardMaturityAlertDto> getForwardAlerts(
        @Parameter(description = "Portfolio ID") 
        @PathVariable Integer portfolioId,
        
        @Parameter(description = "As of date (default: today)") 
        @RequestParam(required = false) 
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) 
        LocalDate asOfDate
    ) {
        log.info("GET /api/v1/forward-alerts/{} - asOfDate: {}", portfolioId, asOfDate);
        
        LocalDate effectiveDate = asOfDate != null ? asOfDate : LocalDate.now();
        ForwardMaturityAlertDto alerts = forwardMaturityAlertService.getForwardMaturityAlerts(
            portfolioId, 
            effectiveDate
        );
        
        return ResponseEntity.ok(alerts);
    }
    
    @GetMapping("/forward-alerts/{portfolioId}/critical")
    @Operation(summary = "Get critical forward alerts only")
    public ResponseEntity<List<ForwardMaturityAlertDto.ForwardAlert>> getCriticalAlerts(
        @PathVariable Integer portfolioId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate
    ) {
        LocalDate effectiveDate = asOfDate != null ? asOfDate : LocalDate.now();
        ForwardMaturityAlertDto alerts = forwardMaturityAlertService.getForwardMaturityAlerts(
            portfolioId, 
            effectiveDate
        );
        
        return ResponseEntity.ok(alerts.criticalAlerts());
    }
    
    // ========================================
    // Tab 6: Position Upload
    // ========================================
    
    @PostMapping("/positions/upload")
    @Operation(summary = "Tab 6: Upload Positions", 
               description = "Upload positions manually or via file")
    public ResponseEntity<PositionUploadDto.UploadResponse> uploadPositions(
        @Valid @RequestBody PositionUploadDto.UploadRequest uploadRequest
    ) {
        log.info("POST /api/v1/positions/upload - portfolio: {}, date: {}", 
            uploadRequest.portfolioId(), 
            uploadRequest.asOfDate()
        );
        
        PositionUploadDto.UploadResponse response = positionUploadService.uploadPositions(uploadRequest);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @GetMapping("/positions/uploads/{portfolioId}")
    @Operation(summary = "Get upload history")
    public ResponseEntity<PositionUploadDto.UploadHistory> getUploadHistory(
        @PathVariable Integer portfolioId
    ) {
        PositionUploadDto.UploadHistory history = positionUploadService.getUploadHistory(portfolioId);
        return ResponseEntity.ok(history);
    }
    
    @GetMapping("/positions/uploads/detail/{uploadId}")
    @Operation(summary = "Get upload details by ID")
    public ResponseEntity<PositionUploadDto.UploadResponse> getUploadById(
        @PathVariable String uploadId
    ) {
        PositionUploadDto.UploadResponse upload = positionUploadService.getUploadById(uploadId);
        return ResponseEntity.ok(upload);
    }
    
    // ========================================
    // Health & Status
    // ========================================
    
    @GetMapping("/health")
    @Operation(summary = "Health check")
    public ResponseEntity<HealthResponse> health() {
        return ResponseEntity.ok(new HealthResponse("UP", "Hedge Service is running"));
    }
    
    public record HealthResponse(String status, String message) {}
}
