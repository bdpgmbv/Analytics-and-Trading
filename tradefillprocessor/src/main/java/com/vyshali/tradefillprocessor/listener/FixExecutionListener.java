package com.vyshali.tradefillprocessor.listener;

/*
 * 12/03/2025 - 1:18 PM
 * @author Vyshali Prabananth Lal
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyshali.tradefillprocessor.dto.ExecutionReportDTO;
import com.vyshali.tradefillprocessor.service.AggregatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FixExecutionListener {

    private final AggregatorService aggregatorService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "RAW_EXECUTION_REPORTS", groupId = "trade-processor-group")
    public void onExecutionReport(ConsumerRecord<String, String> record) {
        try {
            ExecutionReportDTO fill = objectMapper.readValue(record.value(), ExecutionReportDTO.class);
            aggregatorService.processExecution(fill);
        } catch (Exception e) {
            log.error("Failed to process execution report", e);
        }
    }
}
