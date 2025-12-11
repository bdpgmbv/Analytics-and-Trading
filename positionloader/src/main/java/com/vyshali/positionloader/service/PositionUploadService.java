package com.vyshali.positionloader.service;

import com.fxanalyzer.positionloader.config.AppConfig;
import com.fxanalyzer.positionloader.dto.PositionDto;
import com.fxanalyzer.positionloader.dto.UploadResult;
import com.fxanalyzer.positionloader.repository.BatchRepository;
import com.fxanalyzer.positionloader.repository.PositionRepository;
import com.fxanalyzer.positionloader.repository.AuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Position upload service.
 * 
 * Handles:
 * - Manual position uploads
 * - File parsing and validation
 * - Audit logging
 */
@Service
public class PositionUploadService {
    
    private static final Logger log = LoggerFactory.getLogger(PositionUploadService.class);
    
    private final PositionRepository positionRepository;
    private final BatchRepository batchRepository;
    private final PositionValidationService validationService;
    private final AuditRepository auditRepository;
    
    public PositionUploadService(
            PositionRepository positionRepository,
            BatchRepository batchRepository,
            PositionValidationService validationService,
            AuditRepository auditRepository) {
        this.positionRepository = positionRepository;
        this.batchRepository = batchRepository;
        this.validationService = validationService;
        this.auditRepository = auditRepository;
    }
    
    /**
     * Upload positions for an account.
     * Creates a new batch and inserts all positions.
     */
    @Transactional
    public UploadResult uploadPositions(int accountId, LocalDate businessDate, 
            List<PositionDto> positions) {
        
        log.info("Starting position upload: account={} date={} count={}", 
            accountId, businessDate, positions.size());
        
        // Validate
        List<String> errors = validationService.validateForEod(positions, accountId);
        if (!errors.isEmpty()) {
            log.warn("Upload validation failed with {} errors", errors.size());
            return UploadResult.failure(errors);
        }
        
        try {
            // Create new batch
            int batchId = batchRepository.getNextBatchId(accountId);
            batchRepository.createBatch(accountId, batchId, businessDate, 
                AppConfig.BATCH_STATUS_STAGING);
            
            // Insert positions
            positionRepository.insertBatch(positions, batchId);
            
            // Update batch status
            batchRepository.updatePositionCount(accountId, batchId, positions.size());
            batchRepository.updateStatus(accountId, batchId, AppConfig.BATCH_STATUS_ACTIVE);
            batchRepository.setActivatedAt(accountId, batchId, LocalDateTime.now());
            
            // Archive previous batch
            Integer previousBatch = batchRepository.findPreviousActiveBatch(accountId, batchId);
            if (previousBatch != null) {
                batchRepository.updateStatus(accountId, previousBatch, AppConfig.BATCH_STATUS_ARCHIVED);
                batchRepository.setArchivedAt(accountId, previousBatch, LocalDateTime.now());
            }
            
            // Audit log
            auditRepository.logEvent("POSITION_UPLOAD", 
                String.valueOf(accountId),
                "system",
                String.format("Uploaded %d positions for %s, batch %d", 
                    positions.size(), businessDate, batchId));
            
            log.info("Position upload completed: account={} batch={} count={}", 
                accountId, batchId, positions.size());
            
            return UploadResult.success(batchId, positions.size());
            
        } catch (Exception e) {
            log.error("Position upload failed", e);
            return UploadResult.failure(List.of("Upload failed: " + e.getMessage()));
        }
    }
    
    /**
     * Upload positions with source tracking.
     */
    @Transactional
    public UploadResult uploadPositionsWithSource(int accountId, LocalDate businessDate, 
            List<PositionDto> positions, String source, String uploadedBy) {
        
        // Set source on all positions
        List<PositionDto> positionsWithSource = positions.stream()
            .map(p -> p.withSource(source))
            .toList();
        
        UploadResult result = uploadPositions(accountId, businessDate, positionsWithSource);
        
        // Additional audit for manual uploads
        if (result.success()) {
            auditRepository.logEvent("MANUAL_UPLOAD", 
                String.valueOf(accountId),
                uploadedBy,
                String.format("Manual upload: %d positions from %s", 
                    positions.size(), source));
        }
        
        return result;
    }
}
