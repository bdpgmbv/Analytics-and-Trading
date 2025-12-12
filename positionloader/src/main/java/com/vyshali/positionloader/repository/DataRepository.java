package com.vyshali.positionloader.repository;

import com.vyshali.common.dto.SharedDto.DlqMessageDTO;
import com.vyshali.common.repository.AuditRepository;
import com.vyshali.common.repository.DlqRepository;
import com.vyshali.positionloader.dto.Dto;
import com.vyshali.common.dto.PositionDto;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Facade repository that provides unified access to split repositories.
 * Used by controllers and services for simplified data access.
 * 
 * NOTE: Uses common module's AuditRepository and DlqRepository.
 */
@Repository
public class DataRepository {
    
    private final PositionRepository positionRepository;
    private final BatchRepository batchRepository;
    private final EodRepository eodRepository;
    private final ReferenceDataRepository referenceDataRepository;
    
    // ✅ FROM COMMON MODULE
    private final AuditRepository auditRepository;
    private final DlqRepository dlqRepository;
    
    public DataRepository(
            PositionRepository positionRepository,
            BatchRepository batchRepository,
            EodRepository eodRepository,
            ReferenceDataRepository referenceDataRepository,
            AuditRepository auditRepository,      // From common module
            DlqRepository dlqRepository) {        // From common module
        this.positionRepository = positionRepository;
        this.batchRepository = batchRepository;
        this.eodRepository = eodRepository;
        this.referenceDataRepository = referenceDataRepository;
        this.auditRepository = auditRepository;
        this.dlqRepository = dlqRepository;
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
    
    public List<Dto.Position> getPositionsByDate(int accountId, LocalDate businessDate) {
        return positionRepository.findByAccountAndDate(accountId, businessDate)
            .stream()
            .map(this::toPosition)
            .toList();
    }
    
    public List<Dto.Position> getActivePositions(int accountId, LocalDate businessDate) {
        Integer activeBatch = batchRepository.findActiveBatch(accountId);
        if (activeBatch == null) {
            return List.of();
        }
        return positionRepository.findByBatch(activeBatch)
            .stream()
            .map(this::toPosition)
            .toList();
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
    
    public String getEodStatusString(int accountId, LocalDate businessDate) {
        return eodRepository.getStatus(accountId, businessDate);
    }
    
    public Dto.EodStatus getEodStatus(int accountId, LocalDate businessDate) {
        String status = eodRepository.getStatus(accountId, businessDate);
        if (status == null) {
            return null;
        }
        return new Dto.EodStatus(accountId, businessDate, status, null, null, 0, null);
    }
    
    public List<Dto.EodStatus> getEodHistory(int accountId, int days) {
        return eodRepository.getHistory(accountId, days)
            .stream()
            .map(run -> new Dto.EodStatus(
                run.accountId(),
                run.businessDate(),
                run.status(),
                run.startedAt(),
                null,
                0,
                null
            ))
            .toList();
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
    
    // ==================== Audit Operations (Common Module) ====================
    
    /**
     * Log audit event asynchronously.
     */
    public void logAudit(String eventType, int accountId, String details) {
        auditRepository.log(eventType, accountId, details);
    }
    
    /**
     * Log audit event with business date.
     */
    public void logAudit(String eventType, int accountId, LocalDate businessDate, String details) {
        auditRepository.log(eventType, accountId, businessDate, details);
    }
    
    /**
     * Log audit event with actor.
     */
    public void logAudit(String eventType, int accountId, String actor, String details) {
        auditRepository.log(eventType, accountId, actor, details);
    }
    
    // ==================== DLQ Operations (Common Module) ====================
    
    /**
     * Get DLQ depth (pending message count).
     */
    public int getDlqDepth() {
        return dlqRepository.countPending();
    }
    
    /**
     * Get DLQ messages for retry.
     * Returns list of maps for backward compatibility with existing code.
     */
    public List<Map<String, Object>> getDlqMessages(int limit) {
        return dlqRepository.findRetryable(limit).stream()
            .map(this::dlqToMap)
            .toList();
    }
    
    /**
     * Get DLQ messages as DTOs.
     */
    public List<DlqMessageDTO> getDlqMessagesDto(int limit) {
        return dlqRepository.findRetryable(limit);
    }
    
    /**
     * Save message to DLQ.
     */
    public void saveToDlq(String topic, String key, String payload, String error) {
        dlqRepository.insert(topic, key, payload, error);
    }
    
    /**
     * Mark DLQ message as processed (delete).
     */
    public void deleteDlq(Long id) {
        dlqRepository.markProcessed(id);
    }
    
    /**
     * Increment DLQ retry count.
     */
    public void incrementDlqRetry(Long id) {
        dlqRepository.incrementRetry(id, LocalDateTime.now().plusMinutes(5));
    }
    
    /**
     * Mark DLQ message as failed permanently.
     */
    public void markDlqFailed(Long id, String reason) {
        dlqRepository.markFailed(id, reason);
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
    
    /**
     * Get common module's AuditRepository.
     */
    public AuditRepository audit() {
        return auditRepository;
    }
    
    /**
     * Get common module's DlqRepository.
     */
    public DlqRepository dlq() {
        return dlqRepository;
    }
    
    // ==================== Helper Methods ====================
    
    private Dto.Position toPosition(PositionDto dto) {
        return new Dto.Position(
            dto.positionId(),
            dto.accountId(),
            dto.productId(),
            dto.businessDate(),
            dto.quantity(),
            dto.price(),
            dto.currency(),
            dto.marketValueLocal(),
            dto.marketValueBase(),
            dto.source(),
            dto.positionType()
        );
    }
    
    /**
     * Convert DlqMessageDTO to Map for backward compatibility.
     */
    private Map<String, Object> dlqToMap(DlqMessageDTO msg) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", msg.id());
        map.put("topic", msg.topic());
        map.put("message_key", msg.messageKey());
        map.put("payload", msg.payload());
        map.put("error_message", msg.errorMessage());
        map.put("status", msg.status());
        map.put("retry_count", msg.retryCount());
        map.put("created_at", msg.createdAt());
        map.put("next_retry_at", msg.nextRetryAt());
        return map;
    }
}
