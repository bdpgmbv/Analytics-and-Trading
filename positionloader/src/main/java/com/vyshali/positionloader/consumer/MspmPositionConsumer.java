package com.vyshali.positionloader.consumer;

import com.vyshali.fxanalyzer.positionloader.dto.MspmPositionMessage;
import com.vyshali.fxanalyzer.positionloader.service.PositionLoadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Kafka consumer for MSPM position messages.
 * Listens to the mspm.positions topic and processes position snapshots.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MspmPositionConsumer {

    private final PositionLoadService positionLoadService;

    /**
     * Batch listener for position messages.
     * Processes messages in batches for better throughput.
     */
    @KafkaListener(
            topics = "${fxanalyzer.kafka.topics.positions:mspm.positions}",
            containerFactory = "positionKafkaListenerContainerFactory",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consumePositions(
            @Payload List<MspmPositionMessage> messages,
            @Header(KafkaHeaders.RECEIVED_PARTITION) List<Integer> partitions,
            @Header(KafkaHeaders.OFFSET) List<Long> offsets,
            Acknowledgment acknowledgment) {
        
        log.info("Received batch of {} position messages", messages.size());
        
        int successCount = 0;
        int errorCount = 0;
        
        for (int i = 0; i < messages.size(); i++) {
            MspmPositionMessage message = messages.get(i);
            try {
                processMessage(message, partitions.get(i), offsets.get(i));
                successCount++;
            } catch (Exception e) {
                errorCount++;
                log.error("Failed to process message at partition {} offset {}: {}", 
                        partitions.get(i), offsets.get(i), e.getMessage());
                // Continue processing other messages in batch
            }
        }
        
        // Acknowledge the batch
        acknowledgment.acknowledge();
        
        log.info("Batch processing complete: {} success, {} errors", successCount, errorCount);
    }

    /**
     * Process a single message.
     */
    private void processMessage(MspmPositionMessage message, Integer partition, Long offset) {
        log.debug("Processing message from partition {} offset {} - account: {}", 
                partition, offset, message.getAccountNumber());
        
        positionLoadService.processPositionMessage(message);
    }

    /**
     * Single message listener (alternative to batch processing).
     * Can be enabled by changing the configuration.
     */
    // @KafkaListener(
    //         topics = "${fxanalyzer.kafka.topics.positions:mspm.positions}",
    //         groupId = "${spring.kafka.consumer.group-id}-single"
    // )
    public void consumeSinglePosition(
            @Payload MspmPositionMessage message,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        try {
            log.debug("Received position message from partition {} offset {}", partition, offset);
            positionLoadService.processPositionMessage(message);
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process message from partition {} offset {}: {}", 
                    partition, offset, e.getMessage());
            // Don't acknowledge - message will be reprocessed
            throw e;
        }
    }
}
