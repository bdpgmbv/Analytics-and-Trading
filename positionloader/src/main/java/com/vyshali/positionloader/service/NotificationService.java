package com.vyshali.positionloader.service;

/*
 * 12/10/2025 - NEW: Async REST notification service
 *
 * PURPOSE:
 * Replace Kafka for simple cache invalidation notifications to Price/Hedge services.
 *
 * WHY ASYNC REST INSTEAD OF KAFKA:
 * - Cache invalidation is a simple notification (not audit trail)
 * - Kafka adds 10-50ms latency for something that doesn't need guarantees
 * - If notification fails, worst case = stale cache until TTL expires
 * - REST is simpler to debug and monitor
 *
 * PATTERN:
 * - Fire-and-forget with retry
 * - Circuit breaker to prevent cascade failures
 * - Fallback to Kafka if REST repeatedly fails
 *
 * @author Vyshali Prabananth Lal
 */

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class NotificationService {

    private final WebClient priceServiceClient;
    private final WebClient hedgeServiceClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MetricsService metrics;

    // Metrics
    private final AtomicLong restNotificationsSent = new AtomicLong(0);
    private final AtomicLong restNotificationsFailed = new AtomicLong(0);
    private final AtomicLong kafkaFallbackCount = new AtomicLong(0);

    @Value("${features.notifications.async-rest.enabled:true}")
    private boolean asyncRestEnabled;

    @Value("${features.notifications.kafka-fallback.enabled:true}")
    private boolean kafkaFallbackEnabled;

    public NotificationService(WebClient.Builder webClientBuilder, @Value("${services.price-service.url:http://priceservice:8080}") String priceServiceUrl, @Value("${services.hedge-service.url:http://hedgeservice:9898}") String hedgeServiceUrl, @Value("${services.notification.timeout-ms:2000}") int timeoutMs, KafkaTemplate<String, Object> kafkaTemplate, MetricsService metrics) {

        this.priceServiceClient = webClientBuilder.baseUrl(priceServiceUrl).build();

        this.hedgeServiceClient = webClientBuilder.baseUrl(hedgeServiceUrl).build();

        this.kafkaTemplate = kafkaTemplate;
        this.metrics = metrics;

        log.info("NotificationService initialized: priceService={}, hedgeService={}, timeout={}ms", priceServiceUrl, hedgeServiceUrl, timeoutMs);
    }

    // ==================== PUBLIC API ====================

    /**
     * Notify downstream services of position change.
     * Fire-and-forget with retry and fallback.
     */
    @Async
    public CompletableFuture<Void> notifyPositionChange(Integer accountId, Integer clientId, String eventType) {
        log.debug("Sending position change notification: account={}, type={}", accountId, eventType);

        if (!asyncRestEnabled) {
            // Fall back to Kafka directly if REST disabled
            sendKafkaNotification(accountId, clientId, eventType);
            return CompletableFuture.completedFuture(null);
        }

        CacheInvalidationRequest request = new CacheInvalidationRequest(accountId, clientId, eventType, Instant.now().toString());

        // Notify both services in parallel
        CompletableFuture<Void> priceFuture = notifyPriceService(request);
        CompletableFuture<Void> hedgeFuture = notifyHedgeService(request);

        return CompletableFuture.allOf(priceFuture, hedgeFuture).exceptionally(ex -> {
            log.warn("Some notifications failed, continuing: {}", ex.getMessage());
            return null;
        });
    }

    /**
     * Notify Price Service to invalidate cache for an account.
     */
    @Async
    @CircuitBreaker(name = "price-notification", fallbackMethod = "priceNotificationFallback")
    @Retry(name = "price-notification")
    public CompletableFuture<Void> notifyPriceService(CacheInvalidationRequest request) {
        return priceServiceClient.post().uri("/internal/cache/invalidate").contentType(MediaType.APPLICATION_JSON).bodyValue(request).retrieve().toBodilessEntity().timeout(Duration.ofSeconds(2)).doOnSuccess(response -> {
            restNotificationsSent.incrementAndGet();
            log.debug("Price service notified: account={}", request.accountId());
        }).doOnError(error -> {
            restNotificationsFailed.incrementAndGet();
            log.warn("Price service notification failed: {}", error.getMessage());
        }).then().toFuture();
    }

    /**
     * Notify Hedge Service to invalidate cache for an account.
     */
    @Async
    @CircuitBreaker(name = "hedge-notification", fallbackMethod = "hedgeNotificationFallback")
    @Retry(name = "hedge-notification")
    public CompletableFuture<Void> notifyHedgeService(CacheInvalidationRequest request) {
        return hedgeServiceClient.post().uri("/internal/cache/invalidate").contentType(MediaType.APPLICATION_JSON).bodyValue(request).retrieve().toBodilessEntity().timeout(Duration.ofSeconds(2)).doOnSuccess(response -> {
            restNotificationsSent.incrementAndGet();
            log.debug("Hedge service notified: account={}", request.accountId());
        }).doOnError(error -> {
            restNotificationsFailed.incrementAndGet();
            log.warn("Hedge service notification failed: {}", error.getMessage());
        }).then().toFuture();
    }

    // ==================== FALLBACK METHODS ====================

    /**
     * Fallback: Send to Kafka if REST fails.
     */
    public CompletableFuture<Void> priceNotificationFallback(CacheInvalidationRequest request, Throwable t) {
        log.warn("Price notification falling back to Kafka: {}", t.getMessage());
        if (kafkaFallbackEnabled) {
            sendKafkaNotification(request.accountId(), request.clientId(), request.eventType());
        }
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Void> hedgeNotificationFallback(CacheInvalidationRequest request, Throwable t) {
        log.warn("Hedge notification falling back to Kafka: {}", t.getMessage());
        if (kafkaFallbackEnabled) {
            sendKafkaNotification(request.accountId(), request.clientId(), request.eventType());
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Send notification via Kafka (fallback or direct).
     */
    private void sendKafkaNotification(Integer accountId, Integer clientId, String eventType) {
        kafkaFallbackCount.incrementAndGet();

        Map<String, Object> event = Map.of("accountId", accountId, "clientId", clientId != null ? clientId : 0, "eventType", eventType, "timestamp", Instant.now().toString(), "source", "POSITION_LOADER");

        kafkaTemplate.send("POSITION_CHANGE_EVENTS", accountId.toString(), event);
        log.debug("Sent Kafka fallback notification: account={}", accountId);
    }

    // ==================== BATCH NOTIFICATIONS ====================

    /**
     * Notify for multiple accounts (batch EOD completion).
     */
    @Async
    public CompletableFuture<Void> notifyBatchComplete(java.util.List<Integer> accountIds, String eventType) {
        if (accountIds == null || accountIds.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        log.info("Sending batch notification for {} accounts", accountIds.size());

        CompletableFuture<?>[] futures = accountIds.stream().map(accountId -> notifyPositionChange(accountId, null, eventType)).toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(futures);
    }

    // ==================== HEALTH & METRICS ====================

    /**
     * Get notification statistics.
     */
    public NotificationStats getStats() {
        return new NotificationStats(restNotificationsSent.get(), restNotificationsFailed.get(), kafkaFallbackCount.get(), asyncRestEnabled, kafkaFallbackEnabled);
    }

    /**
     * Health check for notification endpoints.
     */
    public CompletableFuture<Map<String, Boolean>> healthCheck() {
        CompletableFuture<Boolean> priceHealth = priceServiceClient.get().uri("/actuator/health").retrieve().toBodilessEntity().timeout(Duration.ofSeconds(2)).map(r -> true).onErrorReturn(false).toFuture();

        CompletableFuture<Boolean> hedgeHealth = hedgeServiceClient.get().uri("/actuator/health").retrieve().toBodilessEntity().timeout(Duration.ofSeconds(2)).map(r -> true).onErrorReturn(false).toFuture();

        return priceHealth.thenCombine(hedgeHealth, (price, hedge) -> Map.of("priceService", price, "hedgeService", hedge));
    }

    // ==================== DTOs ====================

    public record CacheInvalidationRequest(Integer accountId, Integer clientId, String eventType, String timestamp) {
    }

    public record NotificationStats(long restSent, long restFailed, long kafkaFallback, boolean asyncRestEnabled,
                                    boolean kafkaFallbackEnabled) {
    }
}