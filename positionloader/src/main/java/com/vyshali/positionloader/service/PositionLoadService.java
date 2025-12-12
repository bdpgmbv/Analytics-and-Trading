package com.vyshali.positionloader.service;

import com.vyshali.fxanalyzer.common.entity.Snapshot;
import com.vyshali.fxanalyzer.common.event.PositionLoadedEvent;
import com.vyshali.fxanalyzer.positionloader.dto.MspmPositionMessage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * Main service for orchestrating position loading from MSPM.
 * Coordinates snapshot creation, position persistence, and event publishing.
 */
@Slf4j
@Service
public class PositionLoadService {

    private final SnapshotService snapshotService;
    private final PositionPersistenceService positionPersistenceService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    // Metrics
    private final Counter messagesProcessedCounter;
    private final Counter positionsLoadedCounter;
    private final Counter errorsCounter;
    private final Timer processingTimer;

    public PositionLoadService(SnapshotService snapshotService,
                               PositionPersistenceService positionPersistenceService,
                               KafkaTemplate<String, Object> kafkaTemplate,
                               MeterRegistry meterRegistry) {
        this.snapshotService = snapshotService;
        this.positionPersistenceService = positionPersistenceService;
        this.kafkaTemplate = kafkaTemplate;
        
        // Initialize metrics
        this.messagesProcessedCounter = Counter.builder("position.loader.messages.processed")
                .description("Number of MSPM messages processed")
                .register(meterRegistry);
        
        this.positionsLoadedCounter = Counter.builder("position.loader.positions.loaded")
                .description("Number of positions loaded")
                .register(meterRegistry);
        
        this.errorsCounter = Counter.builder("position.loader.errors")
                .description("Number of errors during position loading")
                .register(meterRegistry);
        
        this.processingTimer = Timer.builder("position.loader.processing.time")
                .description("Time taken to process position messages")
                .register(meterRegistry);
    }

    /**
     * Process a single MSPM position message.
     * Creates snapshot, persists positions, and publishes completion event.
     */
    @Transactional
    public void processPositionMessage(MspmPositionMessage message) {
        long startTime = System.nanoTime();
        
        try {
            log.info("Processing position message {} for account {} - {} positions",
                    message.getMessageId(), 
                    message.getAccountNumber(),
                    message.getPositions() != null ? message.getPositions().size() : 0);
            
            // Validate message
            validateMessage(message);
            
            // Create or find snapshot
            Snapshot snapshot = snapshotService.createSnapshot(
                    message.getAccountNumber(),
                    message.getSnapshotType(),
                    message.getSnapshotDate(),
                    message.getSnapshotTime(),
                    message.getSourceSystem()
            );
            
            // Persist positions
            int positionCount = positionPersistenceService.persistPositions(
                    snapshot, message.getPositions());
            
            // Publish completion event
            publishPositionLoadedEvent(snapshot, positionCount);
            
            // Update metrics
            messagesProcessedCounter.increment();
            positionsLoadedCounter.increment(positionCount);
            
            log.info("Successfully processed message {} - {} positions loaded for snapshot {}",
                    message.getMessageId(), positionCount, snapshot.getSnapshotId());
            
        } catch (Exception e) {
            errorsCounter.increment();
            log.error("Failed to process position message {}: {}", 
                    message.getMessageId(), e.getMessage(), e);
            throw e;
        } finally {
            long duration = System.nanoTime() - startTime;
            processingTimer.record(duration, TimeUnit.NANOSECONDS);
        }
    }

    /**
     * Validate incoming MSPM message.
     */
    private void validateMessage(MspmPositionMessage message) {
        if (message.getAccountNumber() == null || message.getAccountNumber().isBlank()) {
            throw new IllegalArgumentException("Account number is required");
        }
        if (message.getSnapshotDate() == null) {
            throw new IllegalArgumentException("Snapshot date is required");
        }
        if (message.getSnapshotType() == null) {
            throw new IllegalArgumentException("Snapshot type is required");
        }
    }

    /**
     * Publish event when positions are loaded successfully.
     */
    private void publishPositionLoadedEvent(Snapshot snapshot, int positionCount) {
        PositionLoadedEvent event = PositionLoadedEvent.builder()
                .snapshotId(snapshot.getSnapshotId())
                .accountNumber(snapshot.getAccount().getAccountNumber())
                .snapshotType(snapshot.getSnapshotType())
                .snapshotDate(snapshot.getSnapshotDate())
                .loadedAt(LocalDateTime.now())
                .positionCount(positionCount)
                .sourceSystem(snapshot.getSourceSystem())
                .build();
        
        try {
            kafkaTemplate.send(PositionLoadedEvent.TOPIC, 
                    snapshot.getAccount().getAccountNumber(), event);
            log.debug("Published PositionLoadedEvent for snapshot {}", snapshot.getSnapshotId());
        } catch (Exception e) {
            log.error("Failed to publish PositionLoadedEvent: {}", e.getMessage());
            // Don't fail the transaction for event publishing failure
        }
    }

    /**
     * Reprocess positions for an existing snapshot.
     */
    @Transactional
    public int reprocessSnapshot(Long snapshotId, MspmPositionMessage message) {
        log.info("Reprocessing snapshot {}", snapshotId);
        
        // Delete existing positions
        positionPersistenceService.deletePositionsForSnapshot(snapshotId);
        
        // Get snapshot and re-persist
        Snapshot snapshot = snapshotService.findActiveSnapshot(
                message.getAccountNumber(), 
                message.getSnapshotType(), 
                message.getSnapshotDate()
        ).orElseThrow(() -> new IllegalStateException("Snapshot not found: " + snapshotId));
        
        return positionPersistenceService.persistPositions(snapshot, message.getPositions());
    }

    /**
     * Get current metrics summary.
     */
    public LoaderMetrics getMetrics() {
        return LoaderMetrics.builder()
                .messagesProcessed((long) messagesProcessedCounter.count())
                .positionsLoaded((long) positionsLoadedCounter.count())
                .errors((long) errorsCounter.count())
                .avgProcessingTimeMs(processingTimer.mean(TimeUnit.MILLISECONDS))
                .build();
    }

    /**
     * Metrics summary DTO
     */
    @lombok.Data
    @lombok.Builder
    public static class LoaderMetrics {
        private long messagesProcessed;
        private long positionsLoaded;
        private long errors;
        private double avgProcessingTimeMs;
    }
}
