package com.vyshali.positionloader.listener;

import com.vyshali.common.service.AlertService;  // ✅ FROM COMMON MODULE
import com.vyshali.common.util.JsonUtils;
import com.vyshali.positionloader.config.LoaderConfig;
import com.vyshali.positionloader.dto.PositionDto.PositionUpdate;
import com.vyshali.positionloader.repository.DataRepository;
import com.vyshali.positionloader.service.PositionService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Kafka message listeners for position updates and EOD triggers.
 * 
 * ✅ Uses common module's AlertService for notifications
 */
@Slf4j
@Component
public class KafkaListeners {

    private final PositionService positionService;
    private final DataRepository dataRepository;
    private final AlertService alertService;  // ✅ FROM COMMON MODULE
    private final LoaderConfig config;
    
    // Metrics
    private final Counter messagesReceived;
    private final Counter messagesProcessed;
    private final Counter messagesFailed;
    private final Counter messagesDlq;

    public KafkaListeners(
            PositionService positionService,
            DataRepository dataRepository,
            AlertService alertService,  // ✅ FROM COMMON MODULE
            LoaderConfig config,
            MeterRegistry meterRegistry) {
        this.positionService = positionService;
        this.dataRepository = dataRepository;
        this.alertService = alertService;
        this.config = config;
        
        // Initialize metrics
        this.messagesReceived = meterRegistry.counter("kafka.messages.received", "listener", "position");
        this.messagesProcessed = meterRegistry.counter("kafka.messages.processed", "listener", "position");
        this.messagesFailed = meterRegistry.counter("kafka.messages.failed", "listener", "position");
        this.messagesDlq = meterRegistry.counter("kafka.messages.dlq", "listener", "position");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // INTRADAY POSITION UPDATES
    // ═══════════════════════════════════════════════════════════════════════════

    @KafkaListener(
        topics = "${app.kafka.topics.position-updates:position-updates}",
        groupId = "${app.kafka.consumer-group:position-loader-group}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPositionUpdate(ConsumerRecord<String, String> record, Acknowledgment ack) {
        messagesReceived.increment();
        String key = record.key();
        String payload = record.value();
        
        log.debug("Received position update: key={}, partition={}, offset={}", 
            key, record.partition(), record.offset());

        try {
            // Check if intraday processing is enabled
            if (!config.features().intradayProcessingEnabled()) {
                log.debug("Intraday processing disabled, skipping message");
                ack.acknowledge();
                return;
            }

            // Parse the update
            PositionUpdate update = JsonUtils.fromJson(payload, PositionUpdate.class);
            if (update == null) {
                log.warn("Failed to parse position update: {}", payload);
                sendToDlq(record.topic(), key, payload, "Parse error: null result");
                ack.acknowledge();
                return;
            }

            // Check if account is disabled
            if (config.features().disabledAccounts().contains(update.accountId())) {
                log.debug("Account {} is disabled, skipping update", update.accountId());
                ack.acknowledge();
                return;
            }

            // Process the update
            positionService.processIntradayUpdate(update);
            messagesProcessed.increment();
            ack.acknowledge();
            
        } catch (Exception e) {
            messagesFailed.increment();
            log.error("Failed to process position update: key={}, error={}", key, e.getMessage(), e);
            
            // Send to DLQ
            sendToDlq(record.topic(), key, payload, e.getMessage());
            
            // Alert on repeated failures
            alertService.warn("Position update processing failed", 
                "Key: " + key + ", Error: " + e.getMessage());
            
            // Still acknowledge to avoid infinite retry loop
            ack.acknowledge();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // EOD TRIGGER
    // ═══════════════════════════════════════════════════════════════════════════

    @KafkaListener(
        topics = "${app.kafka.topics.eod-trigger:eod-trigger}",
        groupId = "${app.kafka.consumer-group:position-loader-group}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onEodTrigger(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String key = record.key();  // Usually accountId
        String payload = record.value();
        
        log.info("Received EOD trigger: key={}", key);

        try {
            // Check if EOD processing is enabled
            if (!config.features().eodProcessingEnabled()) {
                log.warn("EOD processing disabled globally, skipping trigger");
                ack.acknowledge();
                return;
            }

            // Parse account ID from key or payload
            int accountId = parseAccountId(key, payload);
            
            // Check if account is disabled
            if (config.features().disabledAccounts().contains(accountId)) {
                log.warn("Account {} is disabled, skipping EOD", accountId);
                ack.acknowledge();
                return;
            }

            // Trigger EOD processing
            positionService.processEod(accountId);
            
            log.info("EOD processing completed for account {}", accountId);
            ack.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process EOD trigger: key={}, error={}", key, e.getMessage(), e);
            
            // Send to DLQ
            sendToDlq(record.topic(), key, payload, e.getMessage());
            
            // Alert - EOD failures are critical
            alertService.critical("EOD trigger processing failed", 
                "Account: " + key + ", Error: " + e.getMessage());
            
            ack.acknowledge();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BATCH EOD (process multiple accounts)
    // ═══════════════════════════════════════════════════════════════════════════

    @KafkaListener(
        topics = "${app.kafka.topics.batch-eod:batch-eod-trigger}",
        groupId = "${app.kafka.consumer-group:position-loader-group}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onBatchEodTrigger(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String payload = record.value();
        
        log.info("Received batch EOD trigger");

        try {
            if (!config.features().eodProcessingEnabled()) {
                log.warn("EOD processing disabled globally");
                ack.acknowledge();
                return;
            }

            // Parse business date from payload or use today
            LocalDate businessDate = parseBusinessDate(payload);
            
            // Trigger batch EOD
            positionService.processBatchEod(businessDate);
            
            log.info("Batch EOD processing completed for {}", businessDate);
            ack.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process batch EOD trigger: error={}", e.getMessage(), e);
            sendToDlq(record.topic(), "batch", payload, e.getMessage());
            alertService.critical("Batch EOD trigger failed", e.getMessage());
            ack.acknowledge();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // REPROCESS COMMAND (for ops/support)
    // ═══════════════════════════════════════════════════════════════════════════

    @KafkaListener(
        topics = "${app.kafka.topics.reprocess:position-reprocess}",
        groupId = "${app.kafka.consumer-group:position-loader-group}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onReprocessCommand(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String key = record.key();
        String payload = record.value();
        
        log.warn("Received reprocess command: key={}", key);

        try {
            int accountId = parseAccountId(key, payload);
            LocalDate businessDate = parseBusinessDate(payload);
            
            // Reprocess = reset status + re-run EOD
            positionService.reprocessEod(accountId, businessDate);
            
            log.info("Reprocessing completed for account {} date {}", accountId, businessDate);
            alertService.info("Reprocess completed", 
                "Account: " + accountId + ", Date: " + businessDate);
            
            ack.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to reprocess: key={}, error={}", key, e.getMessage(), e);
            sendToDlq(record.topic(), key, payload, e.getMessage());
            alertService.warn("Reprocess failed", "Key: " + key + ", Error: " + e.getMessage());
            ack.acknowledge();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    private void sendToDlq(String topic, String key, String payload, String error) {
        try {
            messagesDlq.increment();
            dataRepository.saveToDlq(topic, key, payload, error);
            log.debug("Message sent to DLQ: topic={}, key={}", topic, key);
        } catch (Exception e) {
            log.error("Failed to send to DLQ: {}", e.getMessage());
        }
    }

    private int parseAccountId(String key, String payload) {
        // Try key first
        if (key != null && !key.isBlank()) {
            try {
                return Integer.parseInt(key.trim());
            } catch (NumberFormatException e) {
                // Try extracting from payload
            }
        }
        
        // Try payload JSON
        if (payload != null && !payload.isBlank()) {
            try {
                var node = JsonUtils.parseTree(payload).orElse(null);
                if (node != null && node.has("accountId")) {
                    return node.get("accountId").asInt();
                }
                if (node != null && node.has("account_id")) {
                    return node.get("account_id").asInt();
                }
            } catch (Exception e) {
                // Fall through
            }
        }
        
        throw new IllegalArgumentException("Cannot parse accountId from key or payload");
    }

    private LocalDate parseBusinessDate(String payload) {
        if (payload != null && !payload.isBlank()) {
            try {
                var node = JsonUtils.parseTree(payload).orElse(null);
                if (node != null && node.has("businessDate")) {
                    return LocalDate.parse(node.get("businessDate").asText());
                }
                if (node != null && node.has("business_date")) {
                    return LocalDate.parse(node.get("business_date").asText());
                }
            } catch (Exception e) {
                // Fall through
            }
        }
        return LocalDate.now();
    }
}
