package com.vyshali.positionloader.service;

/*
 * 12/10/2025 - NEW: Integration tests for IdempotencyService
 *
 * Tests Redis-based duplicate detection with real Redis via Testcontainers.
 *
 * @author Vyshali Prabananth Lal
 */

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IdempotencyServiceIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        registry.add("features.idempotency.enabled", () -> "true");
        registry.add("features.idempotency.ttl-minutes", () -> "1");
        // Disable Kafka for service tests
        registry.add("spring.autoconfigure.exclude", () -> "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration");
    }

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        // Clear all idempotency keys
        idempotencyService.clearAll();
    }

    // ==================== BASIC FUNCTIONALITY TESTS ====================

    @Test
    @Order(1)
    @DisplayName("Should detect new reference as non-duplicate")
    void shouldDetectNewReferenceAsNonDuplicate() {
        // Given
        String refId = "NEW-REF-001";

        // When
        boolean isDuplicate = idempotencyService.isDuplicate(refId);

        // Then
        assertThat(isDuplicate).isFalse();
    }

    @Test
    @Order(2)
    @DisplayName("Should detect marked reference as duplicate")
    void shouldDetectMarkedReferenceAsDuplicate() {
        // Given
        String refId = "MARKED-REF-001";
        idempotencyService.markProcessed(refId);

        // When
        boolean isDuplicate = idempotencyService.isDuplicate(refId);

        // Then
        assertThat(isDuplicate).isTrue();
    }

    @Test
    @Order(3)
    @DisplayName("Should handle check-and-mark atomically")
    void shouldHandleCheckAndMarkAtomically() {
        // Given
        String refId = "ATOMIC-REF-001";

        // When - first call should succeed
        boolean firstResult = idempotencyService.checkAndMark(refId);

        // Then - second call should fail (duplicate)
        boolean secondResult = idempotencyService.checkAndMark(refId);

        assertThat(firstResult).isTrue();
        assertThat(secondResult).isFalse();
    }

    @Test
    @Order(4)
    @DisplayName("Should handle null and blank references gracefully")
    void shouldHandleNullAndBlankReferences() {
        // Null should not be duplicate (allow processing)
        assertThat(idempotencyService.isDuplicate(null)).isFalse();
        assertThat(idempotencyService.checkAndMark(null)).isTrue();

        // Blank should not be duplicate
        assertThat(idempotencyService.isDuplicate("")).isFalse();
        assertThat(idempotencyService.isDuplicate("   ")).isFalse();
    }

    // ==================== BATCH OPERATION TESTS ====================

    @Test
    @Order(5)
    @DisplayName("Should filter duplicates from batch")
    void shouldFilterDuplicatesFromBatch() {
        // Given - mark some as processed
        idempotencyService.markProcessed("BATCH-001");
        idempotencyService.markProcessed("BATCH-003");

        List<String> batch = List.of("BATCH-001", "BATCH-002", "BATCH-003", "BATCH-004");

        // When
        List<String> filtered = idempotencyService.filterDuplicates(batch);

        // Then - only new ones remain
        assertThat(filtered).containsExactly("BATCH-002", "BATCH-004");
    }

    @Test
    @Order(6)
    @DisplayName("Should batch mark as processed")
    void shouldBatchMarkAsProcessed() {
        // Given
        List<String> refs = List.of("BATCH-MARK-001", "BATCH-MARK-002", "BATCH-MARK-003");

        // When
        idempotencyService.markProcessedBatch(refs);

        // Then
        assertThat(idempotencyService.isDuplicate("BATCH-MARK-001")).isTrue();
        assertThat(idempotencyService.isDuplicate("BATCH-MARK-002")).isTrue();
        assertThat(idempotencyService.isDuplicate("BATCH-MARK-003")).isTrue();
    }

    // ==================== CONCURRENCY TESTS ====================

    @Test
    @Order(7)
    @DisplayName("Should handle concurrent check-and-mark correctly")
    void shouldHandleConcurrentCheckAndMark() throws InterruptedException {
        // Given
        String refId = "CONCURRENT-REF-001";
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // When - all threads try to claim the same ref ID
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for signal
                    if (idempotencyService.checkAndMark(refId)) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Start all threads simultaneously
        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        // Then - exactly one thread should succeed
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(threadCount - 1);
    }

    @Test
    @Order(8)
    @DisplayName("Should handle high volume of unique refs")
    void shouldHandleHighVolumeOfUniqueRefs() {
        // Given
        int count = 1000;

        // When
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            idempotencyService.checkAndMark("HIGH-VOL-" + i);
        }
        long duration = System.currentTimeMillis() - startTime;

        // Then
        assertThat(idempotencyService.getTrackedCount()).isGreaterThanOrEqualTo(count);
        assertThat(duration).isLessThan(5000); // Should complete in under 5 seconds

        System.out.println("Processed " + count + " refs in " + duration + "ms");
    }

    // ==================== TTL TESTS ====================

    @Test
    @Order(9)
    @DisplayName("Should expire keys after TTL")
    void shouldExpireKeysAfterTtl() throws InterruptedException {
        // Given - TTL is set to 1 minute in test properties
        String refId = "TTL-REF-001";
        idempotencyService.markProcessed(refId);

        // Verify it's marked
        assertThat(idempotencyService.isDuplicate(refId)).isTrue();

        // Check TTL in Redis directly
        Long ttl = redisTemplate.getExpire("posloader:idem:" + refId);
        assertThat(ttl).isGreaterThan(0);
        assertThat(ttl).isLessThanOrEqualTo(60); // 1 minute
    }

    // ==================== REDIS HEALTH TESTS ====================

    @Test
    @Order(10)
    @DisplayName("Should report Redis as available")
    void shouldReportRedisAsAvailable() {
        assertThat(idempotencyService.isRedisAvailable()).isTrue();
    }

    @Test
    @Order(11)
    @DisplayName("Should track count correctly")
    void shouldTrackCountCorrectly() {
        // Given
        idempotencyService.clearAll();

        // When
        idempotencyService.markProcessed("COUNT-001");
        idempotencyService.markProcessed("COUNT-002");
        idempotencyService.markProcessed("COUNT-003");

        // Then
        assertThat(idempotencyService.getTrackedCount()).isEqualTo(3);
    }

    // ==================== REMOVAL TESTS ====================

    @Test
    @Order(12)
    @DisplayName("Should remove reference correctly")
    void shouldRemoveReferenceCorrectly() {
        // Given
        String refId = "REMOVE-REF-001";
        idempotencyService.markProcessed(refId);
        assertThat(idempotencyService.isDuplicate(refId)).isTrue();

        // When
        idempotencyService.remove(refId);

        // Then
        assertThat(idempotencyService.isDuplicate(refId)).isFalse();
    }
}