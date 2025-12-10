package com.vyshali.positionloader.repository;

/*
 * 12/10/2025 - NEW: Integration tests for PositionRepository
 *
 * Tests batch operations with real PostgreSQL via Testcontainers.
 *
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.positionloader.dto.PositionDto;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PositionRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine").withDatabaseName("positionloader_test").withUsername("test").withPassword("test").withInitScript("db/schema.sql");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Disable Kafka for repository tests
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");
        registry.add("spring.autoconfigure.exclude", () -> "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration");
    }

    @Autowired
    private PositionRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    private static final Integer TEST_ACCOUNT_ID = 12345;
    private static final Integer TEST_CLIENT_ID = 100;
    private static final LocalDate TEST_DATE = LocalDate.of(2025, 12, 10);

    @BeforeEach
    void setUp() {
        // Clean up test data
        jdbc.execute("DELETE FROM positions WHERE account_id = " + TEST_ACCOUNT_ID);
        jdbc.execute("DELETE FROM batch_control WHERE account_id = " + TEST_ACCOUNT_ID);

        // Set up batch control
        jdbc.execute(String.format("INSERT INTO batch_control (account_id, active_batch_id) VALUES (%d, 1) " + "ON CONFLICT (account_id) DO UPDATE SET active_batch_id = 1", TEST_ACCOUNT_ID));
    }

    // ==================== BATCH INSERT TESTS ====================

    @Test
    @Order(1)
    @DisplayName("Should insert single position")
    void shouldInsertSinglePosition() {
        // Given
        PositionDto position = createPosition(1, "REF-001", new BigDecimal("100.50"));

        // When
        int count = repository.insertPositions(List.of(position), 1, TEST_DATE);

        // Then
        assertThat(count).isEqualTo(1);

        Integer dbCount = jdbc.queryForObject("SELECT COUNT(*) FROM positions WHERE account_id = ? AND batch_id = 1", Integer.class, TEST_ACCOUNT_ID);
        assertThat(dbCount).isEqualTo(1);
    }

    @Test
    @Order(2)
    @DisplayName("Should batch insert 500 positions efficiently")
    void shouldBatchInsert500Positions() {
        // Given
        List<PositionDto> positions = IntStream.range(0, 500).mapToObj(i -> createPosition(i, "REF-" + i, new BigDecimal("100.00"))).toList();

        // When
        long startTime = System.currentTimeMillis();
        int count = repository.insertPositions(positions, 1, TEST_DATE);
        long duration = System.currentTimeMillis() - startTime;

        // Then
        assertThat(count).isEqualTo(500);
        assertThat(duration).isLessThan(5000); // Should complete in under 5 seconds

        System.out.println("Batch insert 500 positions took: " + duration + "ms");

        Integer dbCount = jdbc.queryForObject("SELECT COUNT(*) FROM positions WHERE account_id = ? AND batch_id = 1", Integer.class, TEST_ACCOUNT_ID);
        assertThat(dbCount).isEqualTo(500);
    }

    @Test
    @Order(3)
    @DisplayName("Should batch insert 2000 positions in chunks")
    void shouldBatchInsert2000Positions() {
        // Given
        List<PositionDto> positions = IntStream.range(0, 2000).mapToObj(i -> createPosition(i, "REF-" + i, new BigDecimal("50.00"))).toList();

        // When
        long startTime = System.currentTimeMillis();
        int count = repository.insertPositions(positions, 1, TEST_DATE);
        long duration = System.currentTimeMillis() - startTime;

        // Then
        assertThat(count).isEqualTo(2000);
        assertThat(duration).isLessThan(15000); // Should complete in under 15 seconds

        System.out.println("Batch insert 2000 positions took: " + duration + "ms");
    }

    // ==================== BATCH UPDATE TESTS ====================

    @Test
    @Order(4)
    @DisplayName("Should batch update existing positions")
    void shouldBatchUpdatePositions() {
        // Given - insert initial positions
        List<PositionDto> initial = IntStream.range(0, 100).mapToObj(i -> createPosition(i, "REF-" + i, new BigDecimal("100.00"))).toList();
        repository.insertPositions(initial, 1, TEST_DATE);

        // When - update with new quantities
        List<PositionDto> updates = IntStream.range(0, 100).mapToObj(i -> createPosition(i, "REF-" + i, new BigDecimal("200.00"))).toList();
        int count = repository.updatePositions(updates);

        // Then
        assertThat(count).isEqualTo(100);

        // Verify at least some were updated (check first position)
        BigDecimal newPrice = jdbc.queryForObject("SELECT price FROM positions WHERE account_id = ? AND external_ref_id = 'REF-0' AND batch_id = 1", BigDecimal.class, TEST_ACCOUNT_ID);
        assertThat(newPrice).isEqualByComparingTo(new BigDecimal("200.00"));
    }

    // ==================== BATCH CONTROL TESTS ====================

    @Test
    @Order(5)
    @DisplayName("Should get active batch ID with caching")
    void shouldGetActiveBatchIdWithCaching() {
        // Given - batch control already set up in @BeforeEach

        // When - multiple calls
        int batch1 = repository.getActiveBatchId(TEST_ACCOUNT_ID);
        int batch2 = repository.getActiveBatchId(TEST_ACCOUNT_ID);
        int batch3 = repository.getActiveBatchId(TEST_ACCOUNT_ID);

        // Then - all should return same value (cached)
        assertThat(batch1).isEqualTo(1);
        assertThat(batch2).isEqualTo(1);
        assertThat(batch3).isEqualTo(1);
    }

    @Test
    @Order(6)
    @DisplayName("Should swap batch correctly")
    void shouldSwapBatch() {
        // Given
        assertThat(repository.getActiveBatchId(TEST_ACCOUNT_ID)).isEqualTo(1);

        // When
        repository.setActiveBatch(TEST_ACCOUNT_ID, 2);

        // Then (need to evict cache first in real scenario)
        Integer newBatch = jdbc.queryForObject("SELECT active_batch_id FROM batch_control WHERE account_id = ?", Integer.class, TEST_ACCOUNT_ID);
        assertThat(newBatch).isEqualTo(2);
    }

    @Test
    @Order(7)
    @DisplayName("Should clear batch positions")
    void shouldClearBatch() {
        // Given - insert positions in batch 2
        List<PositionDto> positions = IntStream.range(0, 50).mapToObj(i -> createPosition(i, "REF-" + i, new BigDecimal("100.00"))).toList();
        repository.insertPositions(positions, 2, TEST_DATE);

        Integer beforeCount = jdbc.queryForObject("SELECT COUNT(*) FROM positions WHERE account_id = ? AND batch_id = 2", Integer.class, TEST_ACCOUNT_ID);
        assertThat(beforeCount).isEqualTo(50);

        // When
        repository.clearBatch(TEST_ACCOUNT_ID, 2);

        // Then
        Integer afterCount = jdbc.queryForObject("SELECT COUNT(*) FROM positions WHERE account_id = ? AND batch_id = 2", Integer.class, TEST_ACCOUNT_ID);
        assertThat(afterCount).isEqualTo(0);
    }

    // ==================== BITEMPORAL QUERY TESTS ====================

    @Test
    @Order(8)
    @DisplayName("Should query positions as of business date")
    void shouldQueryPositionsAsOfDate() {
        // Given - insert positions with specific business date
        List<PositionDto> positions = IntStream.range(0, 10).mapToObj(i -> createPosition(i, "REF-" + i, new BigDecimal("100.00"))).toList();
        repository.insertPositions(positions, 1, TEST_DATE);

        // When
        List<PositionDto> result = repository.getPositionsAsOf(TEST_ACCOUNT_ID, TEST_DATE);

        // Then
        assertThat(result).hasSize(10);
    }

    @Test
    @Order(9)
    @DisplayName("Should get quantity as of point in time")
    void shouldGetQuantityAsOf() {
        // Given
        PositionDto position = createPosition(999, "REF-999", new BigDecimal("150.00"));
        repository.insertPositions(List.of(position), 1, TEST_DATE);

        // When
        BigDecimal quantity = repository.getQuantityAsOf(TEST_ACCOUNT_ID, 999, LocalDateTime.now());

        // Then
        assertThat(quantity).isEqualByComparingTo(new BigDecimal("1000"));
    }

    // ==================== HELPER METHODS ====================

    private PositionDto createPosition(int productId, String externalRefId, BigDecimal price) {
        return PositionDto.builder().accountId(TEST_ACCOUNT_ID).clientId(TEST_CLIENT_ID).productId(productId).quantity(new BigDecimal("1000")).price(price).marketValue(new BigDecimal("1000").multiply(price)).externalRefId(externalRefId).currency("USD").build();
    }
}