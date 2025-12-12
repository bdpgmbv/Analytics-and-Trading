package com.vyshali.positionloader.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyshali.common.dto.PositionDto;
import com.vyshali.common.service.AlertService;
import com.vyshali.positionloader.config.LoaderConfig;
import com.vyshali.positionloader.service.EodService;
import com.vyshali.positionloader.service.PositionService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Kafka message listeners for position updates and EOD triggers.
 * Handles message processing, error handling, and DLQ routing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaListeners {

    private final PositionService positionService;
    private final EodService eodService;
    private final ObjectMapper objectMapper;
    private final LoaderConfig config;
    private final AlertService alertService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    // ═══════════════════════════════════════════════════════════════════════════
    // INTRADAY POSITION UPDATES
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Listen for intraday position updates from MSPA.
     */
    @KafkaListener(
            topics = "${loader.kafka.intraday-positions:fxan.positions.intraday}",
            groupId = "${spring.kafka.consumer.group-id:fxan-loader}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onIntradayPosition(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String key = record.key();
        String value = record.value();

        log.debug("Received intraday position update: key={}, partition={}, offset={}",
                key, record.partition(), record.offset());

        meterRegistry.counter("kafka.messages.received", "topic", "intraday").increment();

        try {
            // Parse and process the update
            PositionDto.PositionUpdate update = objectMapper.readValue(value, PositionDto.PositionUpdate.class);

            // Check pilot mode
            if (!config.features().shouldProcessAccount(update.accountId())) {
                log.debug("Skipping account {} - not in pilot mode", update.accountId());
                ack.acknowledge();
                return;
            }

            // Process the update
            positionService.processIntradayUpdate(update);

            meterRegistry.counter("kafka.messages.processed", "topic", "intraday", "status", "success").increment();
            ack.acknowledge();

        } catch (JsonProcessingException e) {
            log.error("Failed to parse intraday position message: key={}, error={}", key, e.getMessage());
            handleProcessingError(record, e, "intraday-parse-error");
            ack.acknowledge(); // Ack to avoid infinite retry, message goes to DLQ

        } catch (Exception e) {
            log.error("Failed to process intraday position update: key={}, error={}", key, e.getMessage(), e);
            handleProcessingError(record, e, "intraday-processing-error");
            // Don't ack - let it retry
            throw e;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // EOD POSITION UPDATES
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Listen for EOD position batch messages.
     */
    @KafkaListener(
            topics = "${loader.kafka.eod-positions:fxan.positions.eod}",
            groupId = "${spring.kafka.consumer.group-id:fxan-loader}",
            containerFactory = "batchKafkaListenerContainerFactory"
    )
    public void onEodPositions(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String key = record.key();

        log.info("Received EOD position batch: key={}", key);
        meterRegistry.counter("kafka.messages.received", "topic", "eod").increment();

        try {
            PositionDto.EodBatch batch = objectMapper.readValue(record.value(), PositionDto.EodBatch.class);

            log.info("Processing EOD batch: accountId={}, positionCount={}, businessDate={}",
                    batch.accountId(), batch.positions().size(), batch.businessDate());

            // Process the batch
            eodService.processEodBatch(batch);

            meterRegistry.counter("kafka.messages.processed", "topic", "eod", "status", "success").increment();
            ack.acknowledge();

        } catch (JsonProcessingException e) {
            log.error("Failed to parse EOD batch: key={}, error={}", key, e.getMessage());
            handleProcessingError(record, e, "eod-parse-error");
            ack.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process EOD batch: key={}, error={}", key, e.getMessage(), e);
            handleProcessingError(record, e, "eod-processing-error");
            throw e;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // EOD TRIGGER
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Listen for EOD processing trigger messages.
     * Triggers full position reload for specified accounts.
     */
    @KafkaListener(
            topics = "${loader.kafka.eod-trigger:fxan.eod.trigger}",
            groupId = "${spring.kafka.consumer.group-id:fxan-loader}-trigger",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onEodTrigger(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String key = record.key();

        log.info("Received EOD trigger: key={}", key);
        meterRegistry.counter("kafka.messages.received", "topic", "eod-trigger").increment();

        try {
            PositionDto.EodTrigger trigger = objectMapper.readValue(record.value(), PositionDto.EodTrigger.class);

            log.info("Processing EOD trigger: businessDate={}, accountCount={}",
                    trigger.businessDate(),
                    trigger.accountIds() != null ? trigger.accountIds().size() : "ALL");

            // Trigger EOD processing
            if (trigger.accountIds() != null && !trigger.accountIds().isEmpty()) {
                // Process specific accounts
                for (Integer accountId : trigger.accountIds()) {
                    eodService.processEodForAccount(accountId, trigger.businessDate());
                }
            } else {
                // Process all active accounts
                eodService.processFullEod(trigger.businessDate());
            }

            alertService.info(AlertService.ALERT_EOD_DELAYED, // Using existing alert type
                    "EOD trigger processed successfully",
                    "businessDate=" + trigger.businessDate());

            meterRegistry.counter("kafka.messages.processed", "topic", "eod-trigger", "status", "success").increment();
            ack.acknowledge();

        } catch (JsonProcessingException e) {
            log.error("Failed to parse EOD trigger: key={}, error={}", key, e.getMessage());
            alertService.critical(AlertService.ALERT_EOD_FAILED,
                    "EOD trigger processing failed - parse error: " + e.getMessage(),
                    "key=" + key);
            handleProcessingError(record, e, "trigger-parse-error");
            ack.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process EOD trigger: key={}, error={}", key, e.getMessage(), e);
            alertService.critical(AlertService.ALERT_EOD_FAILED,
                    "EOD trigger processing failed: " + e.getMessage(),
                    "key=" + key);
            handleProcessingError(record, e, "trigger-processing-error");
            throw e;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // REPROCESSING FROM DLQ
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Listen to DLQ for manual reprocessing triggers.
     */
    @KafkaListener(
            topics = "${loader.kafka.dlq:fxan.positions.dlq}.reprocess",
            groupId = "${spring.kafka.consumer.group-id:fxan-loader}-dlq",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onDlqReprocess(ConsumerRecord<String, String> record, Acknowledgment ack) {
        log.info("Reprocessing DLQ message: key={}", record.key());

        try {
            // Parse the DLQ wrapper to get original message
            PositionDto.DlqMessage dlqMessage = objectMapper.readValue(record.value(), PositionDto.DlqMessage.class);

            // Route to appropriate handler based on original topic
            switch (dlqMessage.originalTopic()) {
                case String s when s.contains("intraday") -> {
                    PositionDto.PositionUpdate update = objectMapper.readValue(
                            dlqMessage.originalPayload(), PositionDto.PositionUpdate.class);
                    positionService.processIntradayUpdate(update);
                }
                case String s when s.contains("eod") && !s.contains("trigger") -> {
                    PositionDto.EodBatch batch = objectMapper.readValue(
                            dlqMessage.originalPayload(), PositionDto.EodBatch.class);
                    eodService.processEodBatch(batch);
                }
                case String s when s.contains("trigger") -> {
                    PositionDto.EodTrigger trigger = objectMapper.readValue(
                            dlqMessage.originalPayload(), PositionDto.EodTrigger.class);
                    eodService.processFullEod(trigger.businessDate());
                }
                default -> log.warn("Unknown original topic in DLQ message: {}", dlqMessage.originalTopic());
            }

            alertService.info("dlq-reprocess",
                    "Successfully reprocessed DLQ message",
                    "originalTopic=" + dlqMessage.originalTopic());

            ack.acknowledge();

        } catch (Exception e) {
            log.error("Failed to reprocess DLQ message: {}", e.getMessage(), e);
            alertService.critical("dlq-reprocess-failed",
                    "DLQ reprocessing failed: " + e.getMessage(),
                    "key=" + record.key());
            ack.acknowledge(); // Don't retry forever
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ERROR HANDLING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Handle processing errors by sending to DLQ.
     */
    private void handleProcessingError(ConsumerRecord<String, String> record, Exception e, String errorType) {
        try {
            PositionDto.DlqMessage dlqMessage = new PositionDto.DlqMessage(
                    record.topic(),
                    record.key(),
                    record.value(),
                    e.getMessage(),
                    errorType,
                    LocalDate.now(),
                    System.currentTimeMillis()
            );

            String dlqPayload = objectMapper.writeValueAsString(dlqMessage);
            kafkaTemplate.send(config.kafka().dlq(), record.key(), dlqPayload);

            log.warn("Sent message to DLQ: topic={}, key={}, errorType={}",
                    record.topic(), record.key(), errorType);

            meterRegistry.counter("kafka.dlq.sent", "errorType", errorType).increment();

            // Alert if DLQ is growing
            alertService.warn("dlq-message-added",
                    "Message sent to DLQ: " + errorType,
                    "topic=" + record.topic() + ", key=" + record.key());

        } catch (JsonProcessingException ex) {
            log.error("Failed to send message to DLQ: {}", ex.getMessage());
        }
    }
}