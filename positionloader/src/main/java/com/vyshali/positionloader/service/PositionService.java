package com.vyshali.positionloader.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyshali.positionloader.config.AppConfig;
import com.vyshali.positionloader.config.LoaderConfig;
import com.vyshali.positionloader.dto.Dto;
import com.vyshali.positionloader.dto.EodRequest;
import com.vyshali.positionloader.dto.PositionDto;
import com.vyshali.positionloader.repository.DataRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Main service for position operations.
 * Orchestrates position loading, retrieval, and management.
 */
@Service
public class PositionService {
    
    private static final Logger log = LoggerFactory.getLogger(PositionService.class);
    
    private final DataRepository dataRepository;
    private final EodProcessingService eodProcessingService;
    private final PositionValidationService validationService;
    private final LoaderConfig config;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;
    
    public PositionService(
            DataRepository dataRepository,
            EodProcessingService eodProcessingService,
            PositionValidationService validationService,
            LoaderConfig config,
            MeterRegistry meterRegistry) {
        this.dataRepository = dataRepository;
        this.eodProcessingService = eodProcessingService;
        this.validationService = validationService;
        this.config = config;
        this.meterRegistry = meterRegistry;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules(); // For Java 8 date/time support
    }
    
    /**
     * Get positions for account and date.
     */
    public List<PositionDto> getPositions(int accountId, LocalDate businessDate) {
        log.debug("Getting positions for account {} date {}", accountId, businessDate);
        return dataRepository.findPositions(accountId, businessDate);
    }
    
    /**
     * Get latest positions for account.
     */
    public List<PositionDto> getLatestPositions(int accountId) {
        log.debug("Getting latest positions for account {}", accountId);
        return dataRepository.findLatestPositions(accountId);
    }
    
    /**
     * Process EOD for an account (today's date).
     */
    @Transactional
    public void processEod(int accountId) {
        processEod(accountId, LocalDate.now());
    }
    
    /**
     * Process EOD for an account on a specific date.
     */
    @Transactional
    public void processEod(int accountId, LocalDate businessDate) {
        log.info("Processing EOD for account {} date {}", accountId, businessDate);
        eodProcessingService.processEod(accountId, businessDate);
    }
    
    /**
     * Process EOD request.
     */
    @Transactional
    public EodProcessingService.EodResult processEod(EodRequest request) {
        log.info("Processing EOD request for account {} date {}", 
            request.accountId(), request.businessDate());
        
        if (request.forceReprocess()) {
            return eodProcessingService.reprocessEod(
                request.accountId(), request.businessDate());
        }
        
        return eodProcessingService.processEod(
            request.accountId(), request.businessDate());
    }
    
    /**
     * Process late EOD for a past date.
     */
    @Transactional
    public void processLateEod(int accountId, LocalDate businessDate) {
        log.warn("Processing late EOD for account {} date {}", accountId, businessDate);
        
        // Validate business date is in the past
        if (businessDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Late EOD can only be processed for past dates");
        }
        
        // Force reprocess even if already completed
        eodProcessingService.reprocessEod(accountId, businessDate);
    }
    
    /**
     * Process intraday JSON message from Kafka.
     * 
     * Expected JSON formats:
     * 
     * Single position update:
     * {
     *   "accountId": 1001,
     *   "productId": 1,
     *   "quantity": 100.5,
     *   "price": 150.25,
     *   "currency": "USD"
     * }
     * 
     * Batch position update:
     * {
     *   "accountId": 1001,
     *   "positions": [
     *     {"productId": 1, "quantity": 100.5, "price": 150.25},
     *     {"productId": 2, "quantity": 200.0, "price": 75.50}
     *   ]
     * }
     * 
     * Trade fill event:
     * {
     *   "type": "TRADE_FILL",
     *   "accountId": 1001,
     *   "productId": 1,
     *   "fillQuantity": 50.0,
     *   "fillPrice": 151.00,
     *   "side": "BUY"
     * }
     */
    @Transactional
    public void processIntradayJson(String json) {
        log.debug("Processing intraday JSON: {}", json);
        
        if (json == null || json.isBlank()) {
            log.warn("Received empty intraday message, skipping");
            meterRegistry.counter("posloader.intraday.skipped", "reason", "empty").increment();
            return;
        }
        
        try {
            JsonNode root = objectMapper.readTree(json);
            
            // Check if intraday processing is enabled
            if (!config.features().intradayProcessingEnabled()) {
                log.debug("Intraday processing disabled, skipping message");
                meterRegistry.counter("posloader.intraday.skipped", "reason", "disabled").increment();
                return;
            }
            
            // Determine message type and process accordingly
            String messageType = root.has("type") ? root.get("type").asText() : "POSITION_UPDATE";
            
            switch (messageType) {
                case "TRADE_FILL" -> processTradeFill(root);
                case "POSITION_UPDATE" -> processPositionUpdate(root);
                case "POSITION_BATCH" -> processPositionBatch(root);
                default -> {
                    // Default: treat as position update
                    if (root.has("positions")) {
                        processPositionBatch(root);
                    } else {
                        processPositionUpdate(root);
                    }
                }
            }
            
            meterRegistry.counter("posloader.intraday.processed", "type", messageType).increment();
            
        } catch (JsonProcessingException e) {
            log.error("Failed to parse intraday JSON: {}", e.getMessage());
            meterRegistry.counter("posloader.intraday.failed", "reason", "parse_error").increment();
            throw new RuntimeException("Invalid intraday JSON format", e);
        } catch (Exception e) {
            log.error("Failed to process intraday update: {}", e.getMessage(), e);
            meterRegistry.counter("posloader.intraday.failed", "reason", "processing_error").increment();
            throw new RuntimeException("Intraday processing failed", e);
        }
    }
    
    /**
     * Process a single position update from JSON.
     */
    private void processPositionUpdate(JsonNode node) {
        int accountId = node.get("accountId").asInt();
        int productId = node.get("productId").asInt();
        BigDecimal quantity = getBigDecimal(node, "quantity", BigDecimal.ZERO);
        BigDecimal price = getBigDecimal(node, "price", BigDecimal.ZERO);
        String currency = node.has("currency") ? node.get("currency").asText() : "USD";
        LocalDate businessDate = LocalDate.now();
        
        log.info("Processing intraday position update: account={}, product={}, qty={}", 
            accountId, productId, quantity);
        
        // Validate account
        if (!config.features().shouldProcessAccount(accountId)) {
            log.debug("Account {} not eligible for processing, skipping", accountId);
            return;
        }
        
        if (!dataRepository.isAccountActive(accountId)) {
            log.warn("Account {} not active, skipping intraday update", accountId);
            meterRegistry.counter("posloader.intraday.skipped", "reason", "inactive_account").increment();
            return;
        }
        
        // Validate product
        if (!dataRepository.isProductValid(productId)) {
            log.warn("Product {} not valid, skipping intraday update", productId);
            meterRegistry.counter("posloader.intraday.skipped", "reason", "invalid_product").increment();
            return;
        }
        
        // Create position
        BigDecimal marketValue = quantity.multiply(price);
        PositionDto position = new PositionDto(
            null,
            accountId,
            productId,
            businessDate,
            quantity,
            price,
            currency,
            marketValue,
            marketValue,
            price,
            marketValue,
            0,
            AppConfig.SOURCE_INTRADAY,
            "PHYSICAL",
            false
        );
        
        // Validate position
        if (config.features().validationEnabled()) {
            var validationResult = validationService.validate(position);
            if (!validationResult.isValid()) {
                log.warn("Intraday position validation failed: {}", validationResult.errors());
                meterRegistry.counter("posloader.intraday.validation_failed").increment();
                throw new IllegalArgumentException("Validation failed: " + validationResult.errors());
            }
        }
        
        // Save position (creates new batch for intraday)
        int batchId = dataRepository.createBatch(accountId, businessDate, AppConfig.SOURCE_INTRADAY);
        dataRepository.savePositions(List.of(position), batchId);
        dataRepository.completeBatch(batchId, 1);
        
        dataRepository.logAudit("INTRADAY_UPDATE", accountId, businessDate,
            String.format("Intraday update: product=%d, qty=%s", productId, quantity));
        
        log.info("Intraday position saved: account={}, product={}, batch={}", 
            accountId, productId, batchId);
    }
    
    /**
     * Process a batch of position updates from JSON.
     */
    private void processPositionBatch(JsonNode node) {
        int accountId = node.get("accountId").asInt();
        JsonNode positionsNode = node.get("positions");
        
        if (positionsNode == null || !positionsNode.isArray()) {
            log.warn("No positions array in batch message for account {}", accountId);
            return;
        }
        
        log.info("Processing intraday batch: account={}, count={}", 
            accountId, positionsNode.size());
        
        // Validate account
        if (!config.features().shouldProcessAccount(accountId)) {
            log.debug("Account {} not eligible for processing, skipping batch", accountId);
            return;
        }
        
        LocalDate businessDate = LocalDate.now();
        List<PositionDto> positions = new java.util.ArrayList<>();
        
        for (JsonNode posNode : positionsNode) {
            int productId = posNode.get("productId").asInt();
            BigDecimal quantity = getBigDecimal(posNode, "quantity", BigDecimal.ZERO);
            BigDecimal price = getBigDecimal(posNode, "price", BigDecimal.ZERO);
            String currency = posNode.has("currency") ? posNode.get("currency").asText() : "USD";
            
            BigDecimal marketValue = quantity.multiply(price);
            PositionDto position = new PositionDto(
                null,
                accountId,
                productId,
                businessDate,
                quantity,
                price,
                currency,
                marketValue,
                marketValue,
                price,
                marketValue,
                0,
                AppConfig.SOURCE_INTRADAY,
                "PHYSICAL",
                false
            );
            positions.add(position);
        }
        
        // Validate batch
        if (config.features().validationEnabled()) {
            var validationResult = validationService.validate(positions);
            if (!validationResult.isValid()) {
                log.warn("Intraday batch validation failed: {}", validationResult.errors());
                meterRegistry.counter("posloader.intraday.validation_failed").increment();
                throw new IllegalArgumentException("Batch validation failed: " + validationResult.errors());
            }
        }
        
        // Save batch
        int batchId = dataRepository.createBatch(accountId, businessDate, AppConfig.SOURCE_INTRADAY);
        int saved = dataRepository.savePositions(positions, batchId);
        dataRepository.completeBatch(batchId, saved);
        
        dataRepository.logAudit("INTRADAY_BATCH", accountId, businessDate,
            String.format("Intraday batch: %d positions saved", saved));
        
        log.info("Intraday batch saved: account={}, positions={}, batch={}", 
            accountId, saved, batchId);
    }
    
    /**
     * Process a trade fill event - adjusts existing position.
     */
    private void processTradeFill(JsonNode node) {
        int accountId = node.get("accountId").asInt();
        int productId = node.get("productId").asInt();
        BigDecimal fillQuantity = getBigDecimal(node, "fillQuantity", BigDecimal.ZERO);
        BigDecimal fillPrice = getBigDecimal(node, "fillPrice", BigDecimal.ZERO);
        String side = node.has("side") ? node.get("side").asText() : "BUY";
        
        log.info("Processing trade fill: account={}, product={}, qty={}, side={}", 
            accountId, productId, fillQuantity, side);
        
        // Validate account
        if (!config.features().shouldProcessAccount(accountId)) {
            log.debug("Account {} not eligible for processing, skipping trade fill", accountId);
            return;
        }
        
        // Adjust quantity based on side
        BigDecimal adjustedQuantity = "SELL".equalsIgnoreCase(side) 
            ? fillQuantity.negate() 
            : fillQuantity;
        
        LocalDate businessDate = LocalDate.now();
        
        // Get current position
        List<PositionDto> existingPositions = dataRepository.findPositions(accountId, businessDate);
        PositionDto existing = existingPositions.stream()
            .filter(p -> p.productId() == productId)
            .findFirst()
            .orElse(null);
        
        BigDecimal newQuantity;
        if (existing != null) {
            newQuantity = existing.quantity().add(adjustedQuantity);
        } else {
            newQuantity = adjustedQuantity;
        }
        
        // Create updated position
        BigDecimal marketValue = newQuantity.multiply(fillPrice);
        PositionDto position = new PositionDto(
            null,
            accountId,
            productId,
            businessDate,
            newQuantity,
            fillPrice,
            "USD",
            marketValue,
            marketValue,
            fillPrice,
            marketValue,
            0,
            AppConfig.SOURCE_INTRADAY,
            "PHYSICAL",
            false
        );
        
        // Save position
        int batchId = dataRepository.createBatch(accountId, businessDate, AppConfig.SOURCE_INTRADAY);
        dataRepository.savePositions(List.of(position), batchId);
        dataRepository.completeBatch(batchId, 1);
        
        dataRepository.logAudit("TRADE_FILL", accountId, businessDate,
            String.format("Trade fill: product=%d, side=%s, qty=%s, price=%s", 
                productId, side, fillQuantity, fillPrice));
        
        log.info("Trade fill processed: account={}, product={}, newQty={}", 
            accountId, productId, newQuantity);
    }
    
    /**
     * Helper to safely extract BigDecimal from JSON.
     */
    private BigDecimal getBigDecimal(JsonNode node, String field, BigDecimal defaultValue) {
        if (node.has(field) && !node.get(field).isNull()) {
            return new BigDecimal(node.get(field).asText());
        }
        return defaultValue;
    }
    
    /**
     * Process uploaded positions.
     */
    @Transactional
    public int processUpload(int accountId, List<Dto.Position> positions) {
        log.info("Processing upload of {} positions for account {}", positions.size(), accountId);
        
        // Convert to internal DTO format
        List<PositionDto> positionDtos = positions.stream()
            .map(p -> new PositionDto(
                p.positionId(),
                p.accountId(),
                p.productId(),
                p.businessDate(),
                p.quantity(),
                p.price(),
                p.currency(),
                p.marketValueLocal(),
                p.marketValueBase(),
                BigDecimal.ZERO, // avgCostPrice
                BigDecimal.ZERO, // costLocal
                0, // batchId - will be set
                p.source() != null ? p.source() : AppConfig.SOURCE_UPLOAD,
                p.positionType() != null ? p.positionType() : "PHYSICAL",
                false // isExcluded
            ))
            .toList();
        
        // Validate
        var validationResult = validationService.validate(positionDtos);
        if (!validationResult.isValid()) {
            throw new IllegalArgumentException("Validation failed: " + validationResult.errors());
        }
        
        LocalDate businessDate = positions.isEmpty() ? LocalDate.now() : 
            positions.get(0).businessDate();
        
        // Create batch and save
        int batchId = dataRepository.createBatch(accountId, businessDate, AppConfig.SOURCE_UPLOAD);
        int saved = dataRepository.savePositions(positionDtos, batchId);
        dataRepository.completeBatch(batchId, saved);
        
        dataRepository.logAudit("POSITIONS_UPLOADED", accountId, businessDate,
            String.format("Uploaded %d positions", saved));
        
        return saved;
    }
    
    /**
     * Adjust a position manually.
     */
    @Transactional
    public void adjustPosition(int accountId, int productId, BigDecimal quantity, 
            BigDecimal price, String reason, String actor) {
        log.warn("Manual position adjustment by {} for account {} product {}: qty={}, reason={}", 
            actor, accountId, productId, quantity, reason);
        
        LocalDate businessDate = LocalDate.now();
        
        // Log audit
        dataRepository.logAudit("POSITION_ADJUSTMENT", accountId, businessDate,
            String.format("Product %d adjusted to %s by %s: %s", productId, quantity, actor, reason));
        
        // Create adjustment position
        PositionDto adjustment = PositionDto.of(accountId, productId, businessDate, 
            quantity, price != null ? price : BigDecimal.ZERO, "USD")
            .withSource(AppConfig.SOURCE_ADJUSTMENT);
        
        // Save adjustment
        int batchId = dataRepository.createBatch(accountId, businessDate, AppConfig.SOURCE_ADJUSTMENT);
        dataRepository.savePositions(List.of(adjustment), batchId);
        dataRepository.completeBatch(batchId, 1);
    }
    
    /**
     * Reset EOD status to allow reprocessing.
     */
    @Transactional
    public void resetEodStatus(int accountId, LocalDate businessDate, String actor) {
        log.warn("Resetting EOD status for account {} date {} by {}", accountId, businessDate, actor);
        
        dataRepository.updateEodStatus(accountId, businessDate, AppConfig.EOD_STATUS_PENDING);
        dataRepository.logAudit("EOD_STATUS_RESET", accountId, businessDate,
            String.format("EOD status reset by %s", actor));
    }
    
    /**
     * Rollback to previous batch.
     */
    @Transactional
    public boolean rollbackEod(int accountId, LocalDate businessDate) {
        log.warn("Rolling back EOD for account {} date {}", accountId, businessDate);
        
        boolean success = dataRepository.batches().rollbackToPrevious(accountId);
        
        if (success) {
            dataRepository.logAudit("EOD_ROLLBACK", accountId, businessDate,
                "Rolled back to previous batch");
        }
        
        return success;
    }
    
    /**
     * Save positions for account.
     */
    @Transactional
    public SaveResult savePositions(int accountId, LocalDate businessDate, 
            List<PositionDto> positions, String source) {
        log.info("Saving {} positions for account {} date {}", 
            positions.size(), accountId, businessDate);
        
        // Validate
        var validationResult = validationService.validate(positions);
        if (!validationResult.isValid()) {
            log.warn("Validation failed for account {} date {}: {}", 
                accountId, businessDate, validationResult.errors());
            return SaveResult.validationFailed(validationResult.errors());
        }
        
        // Create batch
        int batchId = dataRepository.createBatch(accountId, businessDate, source);
        
        try {
            // Delete existing positions
            int deleted = dataRepository.deletePositions(accountId, businessDate);
            if (deleted > 0) {
                log.info("Deleted {} existing positions for account {} date {}", 
                    deleted, accountId, businessDate);
            }
            
            // Insert new positions
            int inserted = dataRepository.savePositions(positions, batchId);
            
            // Complete batch
            dataRepository.completeBatch(batchId, inserted);
            
            // Log audit
            dataRepository.logAudit("POSITIONS_SAVED", accountId, businessDate,
                String.format("Saved %d positions from %s", inserted, source));
            
            log.info("Saved {} positions for account {} date {} (batch {})", 
                inserted, accountId, businessDate, batchId);
            
            return SaveResult.success(batchId, inserted);
            
        } catch (Exception e) {
            log.error("Failed to save positions for account {} date {}", 
                accountId, businessDate, e);
            dataRepository.failBatch(batchId, e.getMessage());
            return SaveResult.failed(e.getMessage());
        }
    }
    
    /**
     * Delete positions for account and date.
     */
    @Transactional
    public int deletePositions(int accountId, LocalDate businessDate) {
        log.info("Deleting positions for account {} date {}", accountId, businessDate);
        
        int deleted = dataRepository.deletePositions(accountId, businessDate);
        
        dataRepository.logAudit("POSITIONS_DELETED", accountId, businessDate,
            String.format("Deleted %d positions", deleted));
        
        return deleted;
    }
    
    /**
     * Get position count for account and date.
     */
    public int getPositionCount(int accountId, LocalDate businessDate) {
        return dataRepository.positions().countByAccountAndDate(accountId, businessDate);
    }
    
    /**
     * Get total market value for account and date.
     */
    public BigDecimal getTotalMarketValue(int accountId, LocalDate businessDate) {
        return dataRepository.positions().getTotalMarketValue(accountId, businessDate);
    }
    
    /**
     * Get positions grouped by product type.
     */
    public Map<String, List<PositionDto>> getPositionsByType(int accountId, LocalDate businessDate) {
        List<PositionDto> positions = getPositions(accountId, businessDate);
        return positions.stream()
            .collect(Collectors.groupingBy(PositionDto::positionType));
    }
    
    /**
     * Check if EOD is complete.
     */
    public boolean isEodComplete(int accountId, LocalDate businessDate) {
        return eodProcessingService.isEodComplete(accountId, businessDate);
    }
    
    /**
     * Get EOD status.
     */
    public String getEodStatus(int accountId, LocalDate businessDate) {
        return eodProcessingService.getEodStatus(accountId, businessDate);
    }
    
    /**
     * Result of save operation.
     */
    public record SaveResult(
        Status status,
        int batchId,
        int positionCount,
        List<String> errors
    ) {
        public enum Status {
            SUCCESS, VALIDATION_FAILED, FAILED
        }
        
        public static SaveResult success(int batchId, int count) {
            return new SaveResult(Status.SUCCESS, batchId, count, List.of());
        }
        
        public static SaveResult validationFailed(List<String> errors) {
            return new SaveResult(Status.VALIDATION_FAILED, -1, 0, errors);
        }
        
        public static SaveResult failed(String error) {
            return new SaveResult(Status.FAILED, -1, 0, List.of(error));
        }
        
        public boolean isSuccess() {
            return status == Status.SUCCESS;
        }
    }
}
