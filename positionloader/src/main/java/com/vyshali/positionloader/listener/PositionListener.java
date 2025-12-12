package com.vyshali.positionloader.listener;

import com.vyshali.common.lifecycle.GracefulShutdownManager;
import com.vyshali.common.logging.LogContext;
import com.vyshali.common.repository.DlqRepository;
import com.vyshali.common.util.JsonUtils;
import com.vyshali.positionloader.dto.PositionDto;
import com.vyshali.positionloader.service.PositionService;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Kafka listener for position updates.
 * 
 * ✅ MODIFIED: Now uses common module services:
 *    - GracefulShutdownManager for shutdown coordination
 *    - DlqRepository for dead letter queue (DELETE your local DlqRepository.java)
 *    - LogContext for structured logging
 *    - JsonUtils for safe parsing
 */
@Component
public class PositionListener {

    private static final Logger log = LoggerFactory.getLogger(PositionListener.class);
    private static final String TOPIC = "positions.updates";

    private final PositionService positionService;
    
    // ✅ INJECT FROM COMMON MODULE
    private final GracefulShutdownManager shutdown;
    private final DlqRepository dlqRepository;
    
    private final MeterRegistry metrics;

    public PositionListener(PositionService positionService,
                           GracefulShutdownManager shutdown,
                           DlqRepository dlqRepository,
                           MeterRegistry metrics) {
        this.positionService = positionService;
        this.shutdown = shutdown;
        this.dlqRepository = dlqRepository;
        this.metrics = metrics;
    }

    @KafkaListener(
            topics = TOPIC,
            groupId = "${spring.kafka.consumer.group-id:position-loader}",
            concurrency = "${app.kafka.positions.concurrency:3}"
    )
    public void onPositionUpdate(ConsumerRecord<String, String> record, Acknowledgment ack) {
        // ✅ CHECK SHUTDOWN STATE FIRST
        if (shutdown.isShuttingDown()) {
            log.info("Shutdown in progress, not processing new messages");
            return; // Don't ack - let another instance pick it up
        }

        // ✅ TRACK JOB FOR GRACEFUL SHUTDOWN
        shutdown.jobStarted();
        try {
            processMessage(record);
            ack.acknowledge();
            metrics.counter("position.update.processed", "status", "success").increment();
        } catch (Exception e) {
            handleError(record, e);
            ack.acknowledge(); // Still ack to avoid blocking
        } finally {
            // ✅ MARK JOB AS ENDED
            shutdown.jobEnded();
        }
    }

    private void processMessage(ConsumerRecord<String, String> record) {
        // ✅ Use JsonUtils from common for safe parsing
        PositionDto.PositionUpdate update = JsonUtils.fromJson(record.value(), PositionDto.PositionUpdate.class);

        if (update == null || update.accountId() <= 0) {
            log.warn("Invalid position update received: {}", record.value());
            metrics.counter("position.update.invalid").increment();
            return;
        }

        // ✅ Use LogContext for structured logging
        LogContext.forAccount(update.accountId())
                .and("productId", update.productId())
                .run(() -> {
                    log.debug("Processing position update: {}", update);
                    positionService.updatePosition(update);
                });
    }

    private void handleError(ConsumerRecord<String, String> record, Exception e) {
        log.error("Error processing position update: {}", e.getMessage(), e);
        metrics.counter("position.update.processed", "status", "error").increment();

        // ✅ Use DlqRepository from common (DELETE your local DlqRepository.java)
        dlqRepository.insert(TOPIC, record.key(), record.value(), e.getMessage());
    }
}
