package com.vyshali.positionloader.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyshali.common.dto.PositionDto;
import com.vyshali.common.dto.PositionDto.PositionUpdate;
import com.vyshali.common.service.AlertService;
import com.vyshali.positionloader.config.LoaderConfig;
import com.vyshali.positionloader.service.PositionService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Dedicated listener for real-time position update stream.
 * Separate from KafkaListeners to allow independent scaling.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PositionListener {

    private final PositionService positionService;
    private final ObjectMapper objectMapper;
    private final LoaderConfig config;
    private final AlertService alertService;
    private final MeterRegistry meterRegistry;

    /**
     * Listen for real-time position updates.
     * This is a high-throughput listener for intraday position changes.
     */
    @KafkaListener(
            topics = "${loader.kafka.position-updates:fxan.positions.realtime}",
            groupId = "${spring.kafka.consumer.group-id:fxan-loader}-positions",
            containerFactory = "kafkaListenerContainerFactory",
            concurrency = "${loader.kafka.position-listener-concurrency:3}"
    )
    public void onPositionUpdate(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String key = record.key();

        log.debug("Received position update: key={}, partition={}, offset={}",
                key, record.partition(), record.offset());

        meterRegistry.counter("position.updates.received").increment();

        try {
            // Parse the update
            PositionUpdate update = objectMapper.readValue(record.value(), PositionUpdate.class);

            // Validate
            if (!isValidUpdate(update)) {
                log.warn("Invalid position update received: key={}", key);
                alertService.warn(AlertService.ALERT_VALIDATION_FAILED,
                        "Invalid position update - missing required fields",
                        "key=" + key);
                ack.acknowledge();
                return;
            }

            // Check pilot mode
            if (!config.features().shouldProcessAccount(update.accountId())) {
                log.debug("Skipping position update for account {} - not in pilot", update.accountId());
                ack.acknowledge();
                return;
            }

            // Process the update using the correct method
            positionService.updatePosition(update);

            meterRegistry.counter("position.updates.processed", "status", "success").increment();
            ack.acknowledge();

        } catch (JsonProcessingException e) {
            log.error("Failed to parse position update: key={}, error={}", key, e.getMessage());
            alertService.warn(AlertService.ALERT_VALIDATION_FAILED,
                    "Position update parse error: " + e.getMessage(),
                    "key=" + key);
            meterRegistry.counter("position.updates.processed", "status", "parse-error").increment();
            ack.acknowledge(); // Don't retry parse errors

        } catch (Exception e) {
            log.error("Failed to process position update: key={}, error={}", key, e.getMessage(), e);
            alertService.warn(AlertService.ALERT_POSITION_MISMATCH,
                    "Position update processing failed: " + e.getMessage(),
                    "key=" + key);
            meterRegistry.counter("position.updates.processed", "status", "error").increment();
            // Re-throw to trigger retry
            throw new RuntimeException("Position update processing failed", e);
        }
    }

    /**
     * Validate that update has required fields.
     */
    private boolean isValidUpdate(PositionUpdate update) {
        if (update == null) {
            return false;
        }
        if (update.accountId() <= 0) {
            log.debug("Invalid accountId: {}", update.accountId());
            return false;
        }
        if (update.securityId() == null || update.securityId().isBlank()) {
            log.debug("Missing securityId");
            return false;
        }
        if (update.businessDate() == null) {
            log.debug("Missing businessDate");
            return false;
        }
        return true;
    }
}