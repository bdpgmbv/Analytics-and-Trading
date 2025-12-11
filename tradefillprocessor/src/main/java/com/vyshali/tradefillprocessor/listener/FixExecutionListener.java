package com.vyshali.tradefillprocessor.listener;

/*
 * 12/11/2025 - IMPROVED: Added proper acknowledgment and error handling
 * @author Vyshali Prabananth Lal
 *
 * Kafka listener for execution reports from FX Matrix.
 * Consumes RAW_EXECUTION_REPORTS topic and delegates to AggregatorService.
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyshali.tradefillprocessor.dto.ExecutionReportDTO;
import com.vyshali.tradefillprocessor.service.AggregatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FixExecutionListener {

    private final AggregatorService aggregatorService;
    private final ObjectMapper objectMapper;

    /**
     * Listen for execution reports from FX Matrix.
     *
     * Topic: RAW_EXECUTION_REPORTS
     * Key: orderId
     * Value: ExecutionReportDTO (JSON)
     */
    @KafkaListener(
            topics = "RAW_EXECUTION_REPORTS",
            groupId = "${spring.kafka.consumer.group-id:trade-processor-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onExecutionReport(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String orderId = record.key();
        long offset = record.offset();
        int partition = record.partition();

        log.debug("Received execution report: partition={}, offset={}, key={}",
                partition, offset, orderId);

        try {
            // 1. Deserialize
            ExecutionReportDTO fill = objectMapper.readValue(record.value(), ExecutionReportDTO.class);

            // 2. Validate
            if (!isValid(fill)) {
                log.error("Invalid execution report - missing required fields: {}", record.value());
                ack.acknowledge(); // Acknowledge to prevent reprocessing
                return;
            }

            // 3. Process
            aggregatorService.processExecution(fill);

            // 4. Acknowledge on success
            ack.acknowledge();

            log.debug("Execution report processed successfully: ExecId={}, OrderId={}",
                    fill.execId(), fill.orderId());

        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("Failed to parse execution report: offset={}, error={}",
                    offset, e.getMessage());
            // Acknowledge bad messages to prevent infinite retry
            ack.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process execution report: offset={}, error={}",
                    offset, e.getMessage(), e);
            // Don't acknowledge - let error handler retry or send to DLQ
            throw new RuntimeException("Execution report processing failed", e);
        }
    }

    /**
     * Validate required fields in execution report.
     */
    private boolean isValid(ExecutionReportDTO fill) {
        if (fill == null) return false;
        if (fill.orderId() == null || fill.orderId().isBlank()) return false;
        if (fill.execId() == null || fill.execId().isBlank()) return false;
        if (fill.lastQty() == null) return false;
        if (fill.lastPx() == null) return false;
        return true;
    }
}
