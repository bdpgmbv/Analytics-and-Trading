package com.vyshali.positionloader.repository;

import com.vyshali.positionloader.dto.PositionDto;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Facade repository that provides unified access to split repositories.
 * Used by controllers for simplified data access.
 */
@Repository
public class DataRepository {
    
    private final PositionRepository positionRepository;
    private final BatchRepository batchRepository;
    private final EodRepository eodRepository;
    private final ReferenceDataRepository referenceDataRepository;
    private final AuditRepository auditRepository;
    
    public DataRepository(
            PositionRepository positionRepository,
            BatchRepository batchRepository,
            EodRepository eodRepository,
            ReferenceDataRepository referenceDataRepository,
            AuditRepository auditRepository) {
        this.positionRepository = positionRepository;
        this.batchRepository = batchRepository;
        this.eodRepository = eodRepository;
        this.referenceDataRepository = referenceDataRepository;
        this.auditRepository = auditRepository;
    }
    
    // ==================== Position Operations ====================
    
    public List<PositionDto> findPositions(int accountId, LocalDate businessDate) {
        return positionRepository.findByAccountAndDate(accountId, businessDate);
    }
    
    public List<PositionDto> findLatestPositions(int accountId) {
        return positionRepository.findLatestByAccount(accountId);
    }
    
    public int savePositions(List<PositionDto> positions, int batchId) {
        return positionRepository.batchInsert(positions, batchId);
    }
    
    public int deletePositions(int accountId, LocalDate businessDate) {
        return positionRepository.deleteByAccountAndDate(accountId, businessDate);
    }
    
    // ==================== Batch Operations ====================
    
    public int createBatch(int accountId, LocalDate businessDate, String source) {
        return batchRepository.createBatch(accountId, businessDate, source);
    }
    
    public void updateBatchStatus(int batchId, String status) {
        batchRepository.updateStatus(batchId, status);
    }
    
    public void completeBatch(int batchId, int positionCount) {
        batchRepository.completeBatch(batchId, positionCount);
    }
    
    public void failBatch(int batchId, String errorMessage) {
        batchRepository.failBatch(batchId, errorMessage);
    }
    
    // ==================== EOD Operations ====================
    
    public String getEodStatus(int accountId, LocalDate businessDate) {
        return eodRepository.getStatus(accountId, businessDate);
    }
    
    public void updateEodStatus(int accountId, LocalDate businessDate, String status) {
        eodRepository.updateStatus(accountId, businessDate, status);
    }
    
    public boolean isEodComplete(int accountId, LocalDate businessDate) {
        return eodRepository.isComplete(accountId, businessDate);
    }
    
    // ==================== Reference Data ====================
    
    public boolean isAccountActive(int accountId) {
        return referenceDataRepository.isAccountActive(accountId);
    }
    
    public boolean isProductValid(int productId) {
        return referenceDataRepository.isProductValid(productId);
    }
    
    public String getAccountName(int accountId) {
        return referenceDataRepository.getAccountName(accountId);
    }
    
    // ==================== Audit Operations ====================
    
    public void logAudit(String eventType, int accountId, String details) {
        auditRepository.log(eventType, accountId, details);
    }
    
    public void logAudit(String eventType, int accountId, LocalDate businessDate, String details) {
        auditRepository.log(eventType, accountId, businessDate, details);
    }
    
    // ==================== Direct Repository Access ====================
    
    public PositionRepository positions() {
        return positionRepository;
    }
    
    public BatchRepository batches() {
        return batchRepository;
    }
    
    public EodRepository eod() {
        return eodRepository;
    }
    
    public ReferenceDataRepository referenceData() {
        return referenceDataRepository;
    }
    
    public AuditRepository audit() {
        return auditRepository;
    }
}
