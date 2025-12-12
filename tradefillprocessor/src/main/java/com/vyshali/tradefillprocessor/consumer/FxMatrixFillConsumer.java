package com.vyshali.tradefillprocessor.consumer;

import com.vyshali.fxanalyzer.tradefillprocessor.dto.FxMatrixFillMessage;
import com.vyshali.fxanalyzer.tradefillprocessor.service.TradeExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for FXMatrix trade fill messages.
 * 
 * Listens to the fxmatrix.trades.fills topic for:
 * - Trade acknowledgments (ACK)
 * - Trade executions (FILL)
 * - Trade rejections (REJECT)
 * - Trade cancellations (CANCEL)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FxMatrixFillConsumer {

    private final TradeExecutionService tradeExecutionService;

    /**
     * Listen for trade fill messages from FXMatrix.
     */
    @KafkaListener(
            topics = "${fxanalyzer.kafka.topics.fills:fxmatrix.trades.fills}",
            containerFactory = "fillKafkaListenerContainerFactory",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consumeFill(
            @Payload FxMatrixFillMessage fill,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            Acknowledgment acknowledgment) {
        
        try {
            log.debug("Received fill from partition {} offset {} key {}: {} - {}", 
                    partition, offset, key, fill.getExecutionRef(), fill.getMessageType());
            
            // Process the fill
            tradeExecutionService.processFill(fill);
            
            // Acknowledge successful processing
            acknowledgment.acknowledge();
            
            log.debug("Successfully processed fill {} from partition {} offset {}", 
                    fill.getExecutionRef(), partition, offset);
            
        } catch (Exception e) {
            log.error("Failed to process fill from partition {} offset {}: {}", 
                    partition, offset, e.getMessage(), e);
            
            // Don't acknowledge - message will be reprocessed after retry
            // The error handler in KafkaConfig will handle retries
            throw e;
        }
    }

    /**
     * Dead letter queue listener for failed messages.
     * Messages that fail after all retries end up here.
     */
    @KafkaListener(
            topics = "${fxanalyzer.kafka.topics.fills-dlt:fxmatrix.trades.fills.DLT}",
            groupId = "${spring.kafka.consumer.group-id}-dlt"
    )
    public void consumeFailedFill(
            @Payload FxMatrixFillMessage fill,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        log.error("DLT: Failed fill message from partition {} offset {}: {} - {} - {}", 
                partition, offset, fill.getExecutionRef(), fill.getMessageType(), fill.getStatus());
        
        // Log for manual investigation
        // In production, this could trigger an alert
        
        acknowledgment.acknowledge();
    }
}
