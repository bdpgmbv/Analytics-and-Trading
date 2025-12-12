package com.vyshali.positionloader.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyshali.common.dto.PositionDto;
import com.vyshali.common.dto.PositionDto.PositionUpdate;
import com.vyshali.common.entity.Position;
import com.vyshali.common.repository.PositionRepository;
import com.vyshali.common.service.AlertService;
import com.vyshali.positionloader.config.LoaderConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Core service for position management.
 * Handles position creation, updates, and change event publishing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PositionService {

    private final PositionRepository positionRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final LoaderConfig config;
    private final AlertService alertService;
    private final MeterRegistry meterRegistry;

    // ═══════════════════════════════════════════════════════════════════════════
    // POSITION RETRIEVAL
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get position by ID.
     */
    public Optional<Position> getPosition(Long id) {
        return positionRepository.findById(id);
    }

    /**
     * Get all positions for an account on a business date.
     */
    public List<Position> getPositionsForAccount(int accountId, LocalDate businessDate) {
        return positionRepository.findByAccountIdAndBusinessDate(accountId, businessDate);
    }

    /**
     * Get position by natural key.
     */
    public Optional<Position> getPosition(int accountId, String securityId, LocalDate businessDate) {
        return positionRepository.findByAccountIdAndSecurityIdAndBusinessDate(
                accountId, securityId, businessDate);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // POSITION CREATION & UPDATE
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Create or update a position from DTO.
     */
    @Transactional
    public Position savePosition(PositionDto dto) {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            Position position = findOrCreatePosition(dto.accountId(), dto.securityId(), dto.businessDate());

            // Update position fields
            position.setQuantity(dto.quantity());
            position.setMarketValue(dto.marketValue());
            position.setCostBasis(dto.costBasis());
            position.setCurrency(dto.currency());
            position.setLastUpdated(LocalDateTime.now());

            Position saved = positionRepository.save(position);

            // Publish change event
            publishPositionChange(saved, "UPDATE");

            log.debug("Saved position: accountId={}, securityId={}, quantity={}",
                    dto.accountId(), dto.securityId(), dto.quantity());

            return saved;

        } finally {
            sample.stop(Timer.builder("position.save")
                    .tag("operation", "save")
                    .register(meterRegistry));
        }
    }

    /**
     * Update an existing position (called by PositionListener).
     * This method handles PositionUpdate events from Kafka.
     */
    @Transactional
    public Position updatePosition(PositionUpdate update) {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            log.debug("Processing position update: accountId={}, securityId={}, updateType={}",
                    update.accountId(), update.securityId(), update.updateType());

            // Find existing position or create new one
            Position position = findOrCreatePosition(
                    update.accountId(),
                    update.securityId(),
                    update.businessDate()
            );

            // Apply update based on type
            applyPositionUpdate(position, update);

            // Save and publish
            Position saved = positionRepository.save(position);
            publishPositionChange(saved, update.updateType());

            meterRegistry.counter("position.updates",
                            "type", update.updateType(),
                            "account", String.valueOf(update.accountId()))
                    .increment();

            return saved;

        } catch (Exception e) {
            log.error("Failed to update position: accountId={}, securityId={}, error={}",
                    update.accountId(), update.securityId(), e.getMessage(), e);
            alertService.warn(AlertService.ALERT_POSITION_MISMATCH,
                    "Position update failed: " + e.getMessage(),
                    "accountId=" + update.accountId());
            throw e;
        } finally {
            sample.stop(Timer.builder("position.update")
                    .tag("operation", "update")
                    .register(meterRegistry));
        }
    }

    /**
     * Process intraday position update from Kafka message.
     */
    @Transactional
    public void processIntradayUpdate(PositionUpdate update) {
        log.debug("Processing intraday update for account {} security {}",
                update.accountId(), update.securityId());

        // Check if account should be processed (pilot mode)
        if (!config.features().shouldProcessAccount(update.accountId())) {
            log.debug("Skipping account {} - not in pilot", update.accountId());
            return;
        }

        // Delegate to updatePosition
        updatePosition(update);
    }

    /**
     * Process intraday position update from JSON string.
     */
    @Transactional
    public void processIntradayJson(String json) {
        try {
            PositionUpdate update = objectMapper.readValue(json, PositionUpdate.class);
            processIntradayUpdate(update);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse intraday position JSON: {}", e.getMessage());
            alertService.warn(AlertService.ALERT_VALIDATION_FAILED,
                    "Invalid intraday position JSON",
                    "error=" + e.getMessage());
            throw new RuntimeException("Invalid position JSON", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BATCH OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Save multiple positions in batch.
     */
    @Transactional
    public List<Position> savePositions(List<PositionDto> positions) {
        log.info("Saving batch of {} positions", positions.size());

        return positions.stream()
                .map(this::savePosition)
                .toList();
    }

    /**
     * Delete all positions for an account on a business date.
     * Used before EOD reload.
     */
    @Transactional
    public int deletePositionsForAccount(int accountId, LocalDate businessDate) {
        log.info("Deleting positions for account {} on {}", accountId, businessDate);

        List<Position> positions = positionRepository.findByAccountIdAndBusinessDate(accountId, businessDate);
        positionRepository.deleteAll(positions);

        // Publish delete events
        positions.forEach(p -> publishPositionChange(p, "DELETE"));

        return positions.size();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Find existing position or create a new one.
     */
    private Position findOrCreatePosition(int accountId, String securityId, LocalDate businessDate) {
        return positionRepository.findByAccountIdAndSecurityIdAndBusinessDate(
                        accountId, securityId, businessDate)
                .orElseGet(() -> {
                    Position newPosition = new Position();
                    newPosition.setAccountId(accountId);
                    newPosition.setSecurityId(securityId);
                    newPosition.setBusinessDate(businessDate);
                    newPosition.setCreatedAt(LocalDateTime.now());
                    return newPosition;
                });
    }

    /**
     * Apply update to position based on update type.
     */
    private void applyPositionUpdate(Position position, PositionUpdate update) {
        switch (update.updateType()) {
            case "QUANTITY_CHANGE" -> {
                BigDecimal newQuantity = position.getQuantity() != null
                        ? position.getQuantity().add(update.quantityChange())
                        : update.quantityChange();
                position.setQuantity(newQuantity);
            }
            case "FULL_REPLACE" -> {
                position.setQuantity(update.quantity());
                position.setMarketValue(update.marketValue());
                position.setCostBasis(update.costBasis());
            }
            case "PRICE_UPDATE" -> {
                position.setMarketValue(update.marketValue());
            }
            default -> {
                // Default: update all provided fields
                if (update.quantity() != null) {
                    position.setQuantity(update.quantity());
                }
                if (update.marketValue() != null) {
                    position.setMarketValue(update.marketValue());
                }
                if (update.costBasis() != null) {
                    position.setCostBasis(update.costBasis());
                }
            }
        }

        position.setLastUpdated(LocalDateTime.now());
    }

    /**
     * Publish position change event to Kafka.
     */
    private void publishPositionChange(Position position, String changeType) {
        try {
            PositionDto.PositionChangeEvent event = new PositionDto.PositionChangeEvent(
                    position.getAccountId(),
                    position.getSecurityId(),
                    position.getBusinessDate(),
                    changeType,
                    position.getQuantity(),
                    position.getMarketValue(),
                    LocalDateTime.now()
            );

            String json = objectMapper.writeValueAsString(event);
            String key = position.getAccountId() + "-" + position.getSecurityId();

            kafkaTemplate.send(config.kafka().positionChanges(), key, json);

            log.debug("Published position change: {}", changeType);

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize position change event", e);
        }
    }
}