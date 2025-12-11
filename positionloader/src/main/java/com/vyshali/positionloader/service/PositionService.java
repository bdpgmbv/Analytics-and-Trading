package com.vyshali.positionloader.service;

import com.fxanalyzer.positionloader.dto.PositionDto;
import com.fxanalyzer.positionloader.dto.EodRequest;
import com.fxanalyzer.positionloader.dto.UploadResult;
import com.fxanalyzer.positionloader.repository.PositionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Main position service - orchestrates position operations.
 * 
 * Delegates to specialized services:
 * - EodProcessingService: End-of-day processing
 * - IntradayProcessingService: Real-time position updates
 * - PositionValidationService: Position validation
 * - PositionUploadService: File upload handling
 * - MspmClientService: MSPM API integration
 */
@Service
public class PositionService {
    
    private static final Logger log = LoggerFactory.getLogger(PositionService.class);
    
    private final PositionRepository positionRepository;
    private final EodProcessingService eodProcessingService;
    private final IntradayProcessingService intradayProcessingService;
    private final PositionValidationService validationService;
    private final PositionUploadService uploadService;
    
    public PositionService(
            PositionRepository positionRepository,
            EodProcessingService eodProcessingService,
            IntradayProcessingService intradayProcessingService,
            PositionValidationService validationService,
            PositionUploadService uploadService) {
        this.positionRepository = positionRepository;
        this.eodProcessingService = eodProcessingService;
        this.intradayProcessingService = intradayProcessingService;
        this.validationService = validationService;
        this.uploadService = uploadService;
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // POSITION QUERIES
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Get positions for an account on a specific date.
     */
    @Transactional(readOnly = true)
    public List<PositionDto> getPositions(int accountId, LocalDate businessDate) {
        log.debug("Fetching positions for account {} on {}", accountId, businessDate);
        return positionRepository.findByAccountAndDate(accountId, businessDate);
    }
    
    /**
     * Get active positions (current batch) for an account.
     */
    @Transactional(readOnly = true)
    public List<PositionDto> getActivePositions(int accountId) {
        log.debug("Fetching active positions for account {}", accountId);
        return positionRepository.findActiveByAccount(accountId);
    }
    
    /**
     * Get position count for an account on a date.
     */
    @Transactional(readOnly = true)
    public int getPositionCount(int accountId, LocalDate businessDate) {
        return positionRepository.countByAccountAndDate(accountId, businessDate);
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // EOD PROCESSING (Delegated)
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Process end-of-day positions for an account.
     */
    public void processEod(EodRequest request) {
        log.info("Processing EOD for account {} on {}", 
            request.accountId(), request.businessDate());
        eodProcessingService.processEod(request.accountId(), request.businessDate());
    }
    
    /**
     * Process late EOD (Phase 4 #21).
     */
    public void processLateEod(int accountId, LocalDate businessDate) {
        log.info("Processing late EOD for account {} on {}", accountId, businessDate);
        eodProcessingService.processLateEod(accountId, businessDate);
    }
    
    /**
     * Rollback a batch to previous version.
     */
    public void rollbackBatch(int accountId, int batchId) {
        log.warn("Rolling back batch {} for account {}", batchId, accountId);
        eodProcessingService.rollbackBatch(accountId, batchId);
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // INTRADAY PROCESSING (Delegated)
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Process intraday position update.
     */
    public void processIntradayUpdate(PositionDto position) {
        intradayProcessingService.processUpdate(position);
    }
    
    /**
     * Process trade fill and update positions.
     */
    public void processTradeFill(String orderId, String symbol, String side, 
            double quantity, double price) {
        intradayProcessingService.processTradeFill(orderId, symbol, side, quantity, price);
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // UPLOAD PROCESSING (Delegated)
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Upload positions from file.
     */
    public UploadResult uploadPositions(int accountId, LocalDate businessDate, 
            List<PositionDto> positions) {
        log.info("Uploading {} positions for account {} on {}", 
            positions.size(), accountId, businessDate);
        return uploadService.uploadPositions(accountId, businessDate, positions);
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // VALIDATION (Delegated)
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Validate positions before processing.
     */
    public List<String> validatePositions(List<PositionDto> positions) {
        return validationService.validate(positions);
    }
}
