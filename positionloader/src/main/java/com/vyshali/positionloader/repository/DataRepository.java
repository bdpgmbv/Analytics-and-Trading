package com.vyshali.positionloader.repository;

import com.vyshali.positionloader.dto.Dto;
import com.vyshali.positionloader.dto.PositionDto;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Facade repository that provides unified access to split repositories.
 * Used by controllers and services for simplified data access.
 */
@Repository
public class DataRepository {
    
    private final PositionRepository positionRepository;
    private final BatchRepository batchRepository;
    private final EodRepository eodRepository;
    private final ReferenceDataRepository referenceDataRepository;
    private final AuditRepository auditRepository;
    private final DlqRepository dlqRepository;
    
    public DataRepository(
            PositionRepository positionRepository,
            BatchRepository batchRepository,
            EodRepository eodRepository,
            ReferenceDataRepository referenceDataRepository,
            AuditRepository auditRepository,
            DlqRepository dlqRepository) {
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
    
    // ==================== Audit Operations ====================
    
    public void logAudit(String eventType, int accountId, String details) {
        auditRepository.log(eventType, accountId, details);
    }
    
    public void logAudit(String eventType, int accountId, LocalDate businessDate, String details) {
        auditRepository.log(eventType, accountId, businessDate, details);
    }
    
    // ==================== DLQ Operations ====================
    
    public int getDlqDepth() {
        return dlqRepository.countPending();
    }
    
    public List<Map<String, Object>> getDlqMessages(int limit) {
        return dlqRepository.findRetryable(limit).stream()
            .map(msg -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", msg.id());
                map.put("topic", msg.topic());
                map.put("message_key", msg.messageKey());
                map.put("payload", msg.payload());
                map.put("retry_count", msg.retryCount());
                return map;
            })
            .toList();
    }
    
    public void saveToDlq(String topic, String key, String payload, String error) {
        dlqRepository.insert(topic, key, payload, error);
    }
    
    public void deleteDlq(Long id) {
        dlqRepository.markProcessed(id);
    }
    
    public void incrementDlqRetry(Long id) {
        dlqRepository.incrementRetry(id, java.time.LocalDateTime.now().plusMinutes(5));
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
}
