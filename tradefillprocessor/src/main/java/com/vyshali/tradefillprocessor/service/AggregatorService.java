package com.vyshali.tradefillprocessor.service;

import com.vyshali.common.logging.LogContext;
import com.vyshali.tradefillprocessor.dto.*;
import com.vyshali.tradefillprocessor.repository.TradeRepository;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * IMPROVED: Aggregator with metrics, proper cleanup, and better error handling.
 */
@Slf4j
@Service
public class AggregatorService {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RedisTemplate<String, OrderStateDTO> orderStateRedisTemplate;
    private final TradeRepository tradeRepository;
    private final TradePersistenceService persistenceService;
    private final MeterRegistry meterRegistry;

    private static final String ORDER_STATE_PREFIX = "ORDER_STATE:";
    private static final String PROCESSED_FILL_PREFIX = "PROCESSED_FILL:";
    private static final Duration STATE_TTL = Duration.ofHours(4);
    private static final Duration DEDUP_TTL = Duration.ofHours(24);

    private final Timer fillProcessingTimer;
    private final AtomicInteger pendingOrders = new AtomicInteger(0);

    @Value("${app.trade.max-fills-per-order:100}")
    private int maxFillsPerOrder;

    public AggregatorService(KafkaTemplate<String, Object> kafkaTemplate,
                             RedisTemplate<String, OrderStateDTO> orderStateRedisTemplate,
                             TradeRepository tradeRepository,
                             TradePersistenceService persistenceService,
                             MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.orderStateRedisTemplate = orderStateRedisTemplate;
        this.tradeRepository = tradeRepository;
        this.persistenceService = persistenceService;
        this.meterRegistry = meterRegistry;
        
        // Register metrics
        this.fillProcessingTimer = Timer.builder("trade.fill.processing")
                .description("Time to process execution reports")
                .register(meterRegistry);
        
        meterRegistry.gauge("trade.orders.pending", pendingOrders);
    }

    @Timed(value = "trade.fill.processing", description = "Fill processing time")
    @Transactional
    public void processExecution(ExecutionReportDTO fill) {
        LogContext.with("orderId", fill.orderId())
                .and("execId", fill.execId())
                .and("accountId", fill.accountId())
                .run(() -> doProcessExecution(fill));
    }

    private void doProcessExecution(ExecutionReportDTO fill) {
        Instant start = Instant.now();
        
        try {
            // 1. DEDUPLICATION
            if (isDuplicate(fill.execId())) {
                log.warn("Duplicate fill skipped: {}", fill.execId());
                meterRegistry.counter("trade.fill.duplicate").increment();
                return;
            }

            // 2. PERSIST (audit trail)
            if (!tradeRepository.saveExecution(fill)) {
                return;
            }

            // 3. MARK PROCESSED
            markAsProcessed(fill.execId());

            // 4. UPDATE STATE
            OrderStateDTO state = updateOrderState(fill);
            pendingOrders.set(countPendingOrders());

            // 5. CHECK COMPLETION
            if (isOrderComplete(fill.status(), state)) {
                handleCompletedOrder(state);
            }

            // Record metrics
            Duration elapsed = Duration.between(start, Instant.now());
            fillProcessingTimer.record(elapsed);
            meterRegistry.counter("trade.fill.processed").increment();
            
        } catch (Exception e) {
            meterRegistry.counter("trade.fill.error").increment();
            throw e;
        }
    }

    private boolean isDuplicate(String execId) {
        String key = PROCESSED_FILL_PREFIX + execId;
        return Boolean.TRUE.equals(orderStateRedisTemplate.hasKey(key));
    }

    private void markAsProcessed(String execId) {
        String key = PROCESSED_FILL_PREFIX + execId;
        OrderStateDTO marker = new OrderStateDTO(
            execId, null, null, null, BigDecimal.ZERO, BigDecimal.ZERO, "PROCESSED"
        );
        orderStateRedisTemplate.opsForValue().set(key, marker, DEDUP_TTL);
    }

    private OrderStateDTO updateOrderState(ExecutionReportDTO fill) {
        String key = ORDER_STATE_PREFIX + fill.orderId();
        OrderStateDTO current = orderStateRedisTemplate.opsForValue().get(key);

        BigDecimal filledQty = (current != null) 
            ? current.totalFilledQty().add(fill.lastQty())
            : fill.lastQty();
            
        BigDecimal notional = (current != null)
            ? current.totalNotional().add(fill.lastQty().multiply(fill.lastPx()))
            : fill.lastQty().multiply(fill.lastPx());

        String status = determineStatus(fill.status(), filledQty);

        OrderStateDTO updated = new OrderStateDTO(
            fill.orderId(),
            fill.accountId(),
            fill.ticker(),
            fill.side(),
            filledQty,
            notional,
            status
        );

        orderStateRedisTemplate.opsForValue().set(key, updated, STATE_TTL);
        return updated;
    }

    private String determineStatus(String fillStatus, BigDecimal filledQty) {
        if ("FILLED".equalsIgnoreCase(fillStatus)) return "FILLED";
        if ("REJECTED".equalsIgnoreCase(fillStatus)) return "REJECTED";
        if (filledQty.compareTo(BigDecimal.ZERO) > 0) return "PARTIALLY_FILLED";
        return "NEW";
    }

    private boolean isOrderComplete(String status, OrderStateDTO state) {
        if (Set.of("FILLED", "REJECTED", "CANCELED", "CANCELLED").contains(status.toUpperCase())) {
            return true;
        }
        return tradeRepository.countFillsForOrder(state.orderId()) >= maxFillsPerOrder;
    }

    private void handleCompletedOrder(OrderStateDTO state) {
        log.info("Order complete: orderId={}, filled={}", state.orderId(), state.totalFilledQty());

        BigDecimal vwap = calculateVWAP(state);
        persistenceService.updateOrderSummary(state);

        if (shouldPublish(state)) {
            publishToPositionLoader(state, vwap);
        }

        // Cleanup Redis
        cleanupOrderState(state.orderId());
        meterRegistry.counter("trade.order.completed").increment();
    }

    private BigDecimal calculateVWAP(OrderStateDTO state) {
        if (state.totalFilledQty().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return state.totalNotional().divide(state.totalFilledQty(), 8, RoundingMode.HALF_UP);
    }

    private boolean shouldPublish(OrderStateDTO state) {
        return Set.of("FILLED", "PARTIALLY_FILLED").contains(state.status());
    }

    private void publishToPositionLoader(OrderStateDTO state, BigDecimal vwap) {
        TradeEventDTO event = new TradeEventDTO(
            state.accountId(),
            100, // clientId - lookup in production
            List.of(new TradeEventDTO.PositionDetail(
                null, state.ticker(), state.totalFilledQty(), state.side(), vwap
            ))
        );

        kafkaTemplate.send("MSPA_INTRADAY", state.accountId().toString(), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish trade: {}", ex.getMessage());
                    meterRegistry.counter("trade.publish.error").increment();
                } else {
                    meterRegistry.counter("trade.publish.success").increment();
                }
            });
    }

    private void cleanupOrderState(String orderId) {
        String key = ORDER_STATE_PREFIX + orderId;
        orderStateRedisTemplate.delete(key);
        log.debug("Cleaned up order state: {}", orderId);
    }

    /**
     * IMPROVED: Orphan detection with Redis cleanup.
     */
    public int handleOrphanedOrders(Duration threshold) {
        log.info("Scanning for orphaned orders older than {}", threshold);
        AtomicInteger orphanCount = new AtomicInteger(0);

        Set<String> keys = orderStateRedisTemplate.keys(ORDER_STATE_PREFIX + "*");
        if (keys == null || keys.isEmpty()) return 0;

        Instant cutoff = Instant.now().minus(threshold);

        for (String key : keys) {
            try {
                OrderStateDTO state = orderStateRedisTemplate.opsForValue().get(key);
                if (state == null) continue;

                // Check if stuck in pending state
                if (isPendingState(state.status())) {
                    log.warn("Orphaned order found: orderId={}, status={}", 
                            state.orderId(), state.status());
                    
                    // Update DB
                    persistenceService.markAsOrphaned(state.orderId());
                    
                    // Cleanup Redis
                    orderStateRedisTemplate.delete(key);
                    
                    orphanCount.incrementAndGet();
                    meterRegistry.counter("trade.order.orphaned").increment();
                }
            } catch (Exception e) {
                log.error("Error checking orphan status for key: {}", key, e);
            }
        }

        log.info("Orphan scan complete: {} orders marked", orphanCount.get());
        return orphanCount.get();
    }

    private boolean isPendingState(String status) {
        return Set.of("NEW", "PENDING_NEW", "PARTIALLY_FILLED", "SENT", "ACKNOWLEDGED")
                .contains(status.toUpperCase());
    }

    private int countPendingOrders() {
        Set<String> keys = orderStateRedisTemplate.keys(ORDER_STATE_PREFIX + "*");
        return keys != null ? keys.size() : 0;
    }
}
