package com.vyshali.positionloader.integration;

/*
 * 12/10/2025 - NEW: End-to-end integration test with Kafka
 *
 * Tests full flow: Kafka message → Processing → Database
 *
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.positionloader.dto.PositionDto;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class KafkaIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine").withDatabaseName("positionloader_test").withUsername("test").withPassword("test").withInitScript("db/schema.sql");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0")).withKraft();

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        // Kafka
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        registry.add("spring.kafka.consumer.group-id", () -> "positionloader-test");
    }

    @Autowired
    private JdbcTemplate jdbc;

    private Producer<String, Map<String, Object>> producer;
    private Consumer<String, Map<String, Object>> consumer;

    private static final Integer TEST_ACCOUNT_ID = 99999;
    private static final Integer TEST_CLIENT_ID = 999;
    private static final String INTRADAY_TOPIC = "INTRADAY_POSITION_UPDATES";
    private static final String OUTPUT_TOPIC = "POSITION_CHANGE_EVENTS";

    @BeforeEach
    void setUp() {
        // Set up producer
        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        producer = new DefaultKafkaProducerFactory<String, Map<String, Object>>(producerProps).createProducer();

        // Set up consumer for output events
        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        consumer = new DefaultKafkaConsumerFactory<String, Map<String, Object>>(consumerProps).createConsumer();
        consumer.subscribe(Collections.singletonList(OUTPUT_TOPIC));

        // Clean up test data
        jdbc.execute("DELETE FROM positions WHERE account_id = " + TEST_ACCOUNT_ID);
    }

    @AfterEach
    void tearDown() {
        if (producer != null) producer.close();
        if (consumer != null) consumer.close();
    }

    // ==================== INTRADAY FLOW TESTS ====================

    @Test
    @Order(1)
    @DisplayName("Should process intraday position update from Kafka")
    void shouldProcessIntradayPositionUpdate() {
        // Given - an intraday position update message
        Map<String, Object> message = Map.of("accountId", TEST_ACCOUNT_ID, "clientId", TEST_CLIENT_ID, "productId", 12345, "quantity", 1000, "price", 150.50, "externalRefId", "KAFKA-TEST-001", "currency", "USD");

        // When - send to Kafka
        producer.send(new ProducerRecord<>(INTRADAY_TOPIC, TEST_ACCOUNT_ID.toString(), message));
        producer.flush();

        // Then - wait for processing and verify in database
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM positions WHERE account_id = ? AND external_ref_id = ?", Integer.class, TEST_ACCOUNT_ID, "KAFKA-TEST-001");
            assertThat(count).isGreaterThan(0);
        });
    }

    @Test
    @Order(2)
    @DisplayName("Should deduplicate messages with same external ref")
    void shouldDeduplicateMessages() {
        // Given - same message sent twice
        Map<String, Object> message = Map.of("accountId", TEST_ACCOUNT_ID, "clientId", TEST_CLIENT_ID, "productId", 12345, "quantity", 500, "price", 100.00, "externalRefId", "KAFKA-DUPE-001", "currency", "USD");

        // When - send twice
        producer.send(new ProducerRecord<>(INTRADAY_TOPIC, TEST_ACCOUNT_ID.toString(), message));
        producer.send(new ProducerRecord<>(INTRADAY_TOPIC, TEST_ACCOUNT_ID.toString(), message));
        producer.flush();

        // Then - should only have one position (deduplicated)
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM positions WHERE account_id = ? AND external_ref_id = ?", Integer.class, TEST_ACCOUNT_ID, "KAFKA-DUPE-001");
            assertThat(count).isEqualTo(1);
        });
    }

    @Test
    @Order(3)
    @DisplayName("Should filter zero-price positions")
    void shouldFilterZeroPricePositions() {
        // Given - position with zero price
        Map<String, Object> message = Map.of("accountId", TEST_ACCOUNT_ID, "clientId", TEST_CLIENT_ID, "productId", 99999, "quantity", 1000, "price", 0.0, "externalRefId", "KAFKA-ZERO-001", "currency", "USD");

        // When - send to Kafka
        producer.send(new ProducerRecord<>(INTRADAY_TOPIC, TEST_ACCOUNT_ID.toString(), message));
        producer.flush();

        // Then - should NOT be in database (filtered out)
        // Wait a bit then verify not present
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
        }

        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM positions WHERE account_id = ? AND external_ref_id = ?", Integer.class, TEST_ACCOUNT_ID, "KAFKA-ZERO-001");
        assertThat(count).isEqualTo(0);
    }

    // ==================== BATCH PROCESSING TESTS ====================

    @Test
    @Order(4)
    @DisplayName("Should handle batch of positions")
    void shouldHandleBatchOfPositions() {
        // Given - batch of 10 positions
        for (int i = 0; i < 10; i++) {
            Map<String, Object> message = Map.of("accountId", TEST_ACCOUNT_ID, "clientId", TEST_CLIENT_ID, "productId", 10000 + i, "quantity", 100 * (i + 1), "price", 50.0 + i, "externalRefId", "KAFKA-BATCH-" + i, "currency", "USD");
            producer.send(new ProducerRecord<>(INTRADAY_TOPIC, TEST_ACCOUNT_ID.toString(), message));
        }
        producer.flush();

        // Then - all 10 should be in database
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM positions WHERE account_id = ? AND external_ref_id LIKE 'KAFKA-BATCH-%'", Integer.class, TEST_ACCOUNT_ID);
            assertThat(count).isEqualTo(10);
        });
    }

    // ==================== OUTPUT EVENT TESTS ====================

    @Test
    @Order(5)
    @DisplayName("Should publish position change event after processing")
    void shouldPublishPositionChangeEvent() {
        // Given - a valid position update
        Map<String, Object> message = Map.of("accountId", TEST_ACCOUNT_ID, "clientId", TEST_CLIENT_ID, "productId", 55555, "quantity", 200, "price", 75.25, "externalRefId", "KAFKA-EVENT-001", "currency", "USD");

        // When - send to Kafka
        producer.send(new ProducerRecord<>(INTRADAY_TOPIC, TEST_ACCOUNT_ID.toString(), message));
        producer.flush();

        // Then - should receive output event
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            ConsumerRecords<String, Map<String, Object>> records = consumer.poll(Duration.ofMillis(500));
            assertThat(records.count()).isGreaterThan(0);
        });
    }

    // ==================== ERROR HANDLING TESTS ====================

    @Test
    @Order(6)
    @DisplayName("Should handle malformed message gracefully")
    void shouldHandleMalformedMessageGracefully() {
        // Given - malformed message (missing required fields)
        Map<String, Object> message = Map.of("someRandomField", "value");

        // When - send to Kafka (should not crash the consumer)
        producer.send(new ProducerRecord<>(INTRADAY_TOPIC, "invalid", message));
        producer.flush();

        // Then - service should still be running (send a valid message after)
        Map<String, Object> validMessage = Map.of("accountId", TEST_ACCOUNT_ID, "clientId", TEST_CLIENT_ID, "productId", 77777, "quantity", 100, "price", 50.0, "externalRefId", "KAFKA-AFTER-ERROR-001", "currency", "USD");
        producer.send(new ProducerRecord<>(INTRADAY_TOPIC, TEST_ACCOUNT_ID.toString(), validMessage));
        producer.flush();

        // Valid message should still be processed
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM positions WHERE account_id = ? AND external_ref_id = ?", Integer.class, TEST_ACCOUNT_ID, "KAFKA-AFTER-ERROR-001");
            assertThat(count).isEqualTo(1);
        });
    }
}