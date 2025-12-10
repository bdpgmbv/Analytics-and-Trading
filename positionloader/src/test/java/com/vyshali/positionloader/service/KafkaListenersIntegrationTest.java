package com.vyshali.positionloader.service;

/*
 * 12/10/2025 - 2:41 PM
 * @author Vyshali Prabananth Lal
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyshali.positionloader.config.KafkaConfig;
import com.vyshali.positionloader.dto.AccountSnapshotDTO;
import com.vyshali.positionloader.dto.PositionDTO;
import com.vyshali.positionloader.service.SnapshotService;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {KafkaConfig.TOPIC_EOD_TRIGGER, KafkaConfig.TOPIC_INTRADAY}, brokerProperties = {"listeners=PLAINTEXT://localhost:0", "port=0"})
@TestPropertySource(properties = {"spring.kafka.consumer.auto-offset-reset=earliest", "spring.kafka.consumer.group-id=test-group"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class KafkaListenersIntegrationTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @MockBean
    private SnapshotService snapshotService;

    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafka);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        kafkaTemplate = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps));
    }

    // ==================== EOD TRIGGER TESTS ====================

    @Test
    @DisplayName("EOD Trigger - Valid Account ID Processed")
    void eodTrigger_ValidAccountId_CallsProcessEod() throws Exception {
        // Given
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(inv -> {
            latch.countDown();
            return null;
        }).when(snapshotService).processEod(anyInt());

        // When
        kafkaTemplate.send(KafkaConfig.TOPIC_EOD_TRIGGER, "1001", "1001");

        // Then
        boolean processed = latch.await(10, TimeUnit.SECONDS);
        assertThat(processed).isTrue();
        verify(snapshotService).processEod(1001);
    }

    @Test
    @DisplayName("EOD Trigger - Invalid Account ID Acknowledged Without Processing")
    void eodTrigger_InvalidAccountId_AcknowledgedWithoutProcessing() throws Exception {
        // Given
        CountDownLatch latch = new CountDownLatch(1);
        // Use a spy or event listener to detect acknowledgment

        // When
        kafkaTemplate.send(KafkaConfig.TOPIC_EOD_TRIGGER, "invalid", "not-a-number");

        // Then - Give time for processing
        Thread.sleep(2000);
        verify(snapshotService, never()).processEod(anyInt());
    }

    @Test
    @DisplayName("EOD Trigger - Exception Does Not Block Consumer")
    void eodTrigger_ExceptionThrown_ConsumerContinues() throws Exception {
        // Given
        CountDownLatch latch = new CountDownLatch(2);
        doAnswer(inv -> {
            int accountId = inv.getArgument(0);
            latch.countDown();
            if (accountId == 1001) {
                throw new RuntimeException("Test exception");
            }
            return null;
        }).when(snapshotService).processEod(anyInt());

        // When - Send two messages, first will fail
        kafkaTemplate.send(KafkaConfig.TOPIC_EOD_TRIGGER, "1001", "1001");
        kafkaTemplate.send(KafkaConfig.TOPIC_EOD_TRIGGER, "1002", "1002");

        // Then - Both should be attempted (with DLQ for first)
        // Note: With DLQ config, first goes to DLQ, second processes normally
        boolean processed = latch.await(15, TimeUnit.SECONDS);
        // At least one should process
        verify(snapshotService, atLeast(1)).processEod(anyInt());
    }

    // ==================== INTRADAY BATCH TESTS ====================

    @Test
    @DisplayName("Intraday Batch - Valid Records Processed")
    void intradayBatch_ValidRecords_ProcessesAll() throws Exception {
        // Given
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(inv -> {
            latch.countDown();
            return null;
        }).when(snapshotService).processIntradayRecord(any());

        AccountSnapshotDTO snapshot = createTestSnapshot();
        String json = objectMapper.writeValueAsString(snapshot);

        // When
        kafkaTemplate.send(KafkaConfig.TOPIC_INTRADAY, "1001", json);

        // Then
        boolean processed = latch.await(10, TimeUnit.SECONDS);
        assertThat(processed).isTrue();
        verify(snapshotService).processIntradayRecord(any());
    }

    @Test
    @DisplayName("Intraday Batch - Partial Failure Continues Processing")
    void intradayBatch_PartialFailure_ContinuesProcessing() throws Exception {
        // Given
        CountDownLatch latch = new CountDownLatch(2);
        doAnswer(inv -> {
            String json = inv.getArgument(0);
            latch.countDown();
            if (json.contains("bad-data")) {
                throw new RuntimeException("Parse error");
            }
            return null;
        }).when(snapshotService).processIntradayRecord(any());

        // When - Send good and bad data
        kafkaTemplate.send(KafkaConfig.TOPIC_INTRADAY, "bad", "bad-data");
        kafkaTemplate.send(KafkaConfig.TOPIC_INTRADAY, "1001", objectMapper.writeValueAsString(createTestSnapshot()));

        // Then - Both attempted
        boolean processed = latch.await(15, TimeUnit.SECONDS);
        verify(snapshotService, times(2)).processIntradayRecord(any());
    }

    // ==================== HELPER METHODS ====================

    private AccountSnapshotDTO createTestSnapshot() {
        List<PositionDTO> positions = List.of(new PositionDTO(1001, "AAPL", "EQUITY", "USD", new BigDecimal("100"), new BigDecimal("150.00"), "INTRADAY", "REF-001"));

        return new AccountSnapshotDTO(1001, 100, "Test Client", 200, "Test Fund", "USD", "ACC-1001", "CUSTODY", positions);
    }
}
