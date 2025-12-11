package com.vyshali.tradefillprocessor.service;

/*
 * 12/11/2025 - COMPLETED: Full VWAP aggregation with Redis state
 * @author Vyshali Prabananth Lal
 *
 * Core service for processing execution reports from FX Matrix.
 * Aggregates partial fills into complete orders and publishes to Position Loader.
 *
 * Flow:
 * 1. Receive ExecutionReport from Kafka
 * 2. Deduplicate using execId
 * 3. Update Redis state (accumulate fills)
 * 4. Calculate VWAP when order complete
 * 5. Persist to database
 * 6. Publish to Position Loader
 */

import com.vyshali.tradefillprocessor.dto.*;
import com.vyshali.tradefillprocessor.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AggregatorService {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RedisTemplate<String, OrderStateDTO> orderStateRedisTemplate;
    private final TradeRepository tradeRepository;
    private final TradePersistenceService persistenceService;

    private static final String ORDER_STATE_PREFIX = "ORDER_STATE:";
    private static final String PROCESSED_FILLS_PREFIX = "PROCESSED_FILL:";

    @Value("${app.trade.aggregation-window-ms:5000}")
    private long aggregationWindowMs;

    @Value("${app.trade.max-fills-per-order:100}")
    private int maxFillsPerOrder;

    /**
     * Main entry point for processing execution reports.
     * Handles deduplication, aggregation, and downstream publishing.
     */
    @Transactional
    public void processExecution(ExecutionReportDTO fill) {
        String orderId = fill.orderId();
        String execId = fill.execId();

        log.info("Processing execution: OrderId={}, ExecId={}, Status={}, Qty={}, Price={}",
                orderId, execId, fill.status(), fill.lastQty(), fill.lastPx());

        // 1. DEDUPLICATION - Check if we've already processed this fill
        if (isDuplicate(execId)) {
            log.warn("Duplicate fill detected, skipping: ExecId={}", execId);
            return;
        }

        // 2. PERSIST FILL - Save to database first (audit trail)
        boolean saved = tradeRepository.saveExecution(fill);
        if (!saved) {
            log.warn("Fill already in database: ExecId={}", execId);
            return;
        }

        // 3. MARK AS PROCESSED - Prevent re-processing
        markAsProcessed(execId);

        // 4. UPDATE STATE - Accumulate fill into order state
        OrderStateDTO updatedState = updateOrderState(fill);

        // 5. CHECK COMPLETION - Determine if order is complete
        if (isOrderComplete(fill.status(), updatedState)) {
            handleCompletedOrder(updatedState, fill);
        } else {
            log.info("Order partially filled: OrderId={}, TotalFilled={}, Status={}",
                    orderId, updatedState.totalFilledQty(), updatedState.status());
        }
    }

    /**
     * Check if this fill has already been processed (Redis-based deduplication).
     */
    private boolean isDuplicate(String execId) {
        String key = PROCESSED_FILLS_PREFIX + execId;
        return Boolean.TRUE.equals(orderStateRedisTemplate.hasKey(key));
    }

    /**
     * Mark fill as processed with TTL (24 hours).
     */
    private void markAsProcessed(String execId) {
        String key = PROCESSED_FILLS_PREFIX + execId;
        // Store a dummy value with TTL
        orderStateRedisTemplate.opsForValue().set(
                key,
                new OrderStateDTO(execId, null, null, null, BigDecimal.ZERO, BigDecimal.ZERO, "PROCESSED"),
                Duration.ofHours(24)
        );
    }

    /**
     * Update order state in Redis with new fill.
     * Calculates running totals for VWAP computation.
     */
    private OrderStateDTO updateOrderState(ExecutionReportDTO fill) {
        String key = ORDER_STATE_PREFIX + fill.orderId();

        // Get current state or create new
        OrderStateDTO currentState = orderStateRedisTemplate.opsForValue().get(key);

        BigDecimal newFilledQty;
        BigDecimal newTotalNotional;

        if (currentState == null) {
            // First fill for this order
            newFilledQty = fill.lastQty();
            newTotalNotional = fill.lastQty().multiply(fill.lastPx());
        } else {
            // Accumulate
            newFilledQty = currentState.totalFilledQty().add(fill.lastQty());
            newTotalNotional = currentState.totalNotional()
                    .add(fill.lastQty().multiply(fill.lastPx()));
        }

        // Determine status
        String status = fill.status();
        if ("FILLED".equalsIgnoreCase(fill.status())) {
            status = "FILLED";
        } else if (newFilledQty.compareTo(BigDecimal.ZERO) > 0) {
            status = "PARTIALLY_FILLED";
        }

        // Create updated state
        OrderStateDTO updatedState = new OrderStateDTO(
                fill.orderId(),
                fill.accountId(),
                fill.ticker(),
                fill.side(),
                newFilledQty,
                newTotalNotional,
                status
        );

        // Save to Redis with TTL
        orderStateRedisTemplate.opsForValue().set(key, updatedState, Duration.ofHours(4));

        return updatedState;
    }

    /**
     * Check if order is complete based on status or fill count.
     */
    private boolean isOrderComplete(String status, OrderStateDTO state) {
        // Complete statuses
        if ("FILLED".equalsIgnoreCase(status) ||
                "REJECTED".equalsIgnoreCase(status) ||
                "CANCELED".equalsIgnoreCase(status) ||
                "CANCELLED".equalsIgnoreCase(status)) {
            return true;
        }

        // Safety: Force completion if too many fills
        int fillCount = tradeRepository.countFillsForOrder(state.orderId());
        if (fillCount >= maxFillsPerOrder) {
            log.warn("Max fills reached for order: OrderId={}, Count={}", state.orderId(), fillCount);
            return true;
        }

        return false;
    }

    /**
     * Handle a completed order:
     * 1. Calculate VWAP
     * 2. Persist final state
     * 3. Publish to Position Loader
     * 4. Clean up Redis
     */
    private void handleCompletedOrder(OrderStateDTO state, ExecutionReportDTO lastFill) {
        log.info("Order complete: OrderId={}, TotalFilled={}, Status={}",
                state.orderId(), state.totalFilledQty(), state.status());

        // 1. Calculate VWAP (Volume Weighted Average Price)
        BigDecimal vwap = calculateVWAP(state);
        log.info("VWAP calculated: OrderId={}, VWAP={}", state.orderId(), vwap);

        // 2. Persist final order summary
        persistenceService.updateOrderSummary(state);

        // 3. Publish to Position Loader (only for filled orders)
        if ("FILLED".equalsIgnoreCase(state.status()) ||
                "PARTIALLY_FILLED".equalsIgnoreCase(state.status())) {
            publishToPositionLoader(state, vwap);
        }

        // 4. Clean up Redis state
        String key = ORDER_STATE_PREFIX + state.orderId();
        orderStateRedisTemplate.delete(key);
    }

    /**
     * Calculate Volume Weighted Average Price.
     * VWAP = Total Notional / Total Quantity
     */
    private BigDecimal calculateVWAP(OrderStateDTO state) {
        if (state.totalFilledQty().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return state.totalNotional().divide(
                state.totalFilledQty(),
                8,
                RoundingMode.HALF_UP
        );
    }

    /**
     * Publish completed trade to Position Loader for position update.
     */
    private void publishToPositionLoader(OrderStateDTO state, BigDecimal vwap) {
        // Build trade event for Position Loader
        TradeEventDTO event = new TradeEventDTO(
                state.accountId(),
                100, // Client ID - should be looked up in production
                List.of(new TradeEventDTO.PositionDetail(
                        null, // Product ID - resolved by Position Loader
                        state.ticker(),
                        state.totalFilledQty(),
                        state.side(),
                        vwap
                ))
        );

        log.info("Publishing trade to Position Loader: Account={}, Ticker={}, Qty={}, VWAP={}",
                state.accountId(), state.ticker(), state.totalFilledQty(), vwap);

        kafkaTemplate.send("MSPA_INTRADAY", state.accountId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish trade event: OrderId={}, Error={}",
                                state.orderId(), ex.getMessage());
                    } else {
                        log.info("Trade event published successfully: OrderId={}, Offset={}",
                                state.orderId(), result.getRecordMetadata().offset());
                    }
                });
    }

    /**
     * Handle orphaned orders (no fills received within threshold).
     * Called by scheduled job.
     */
    public void handleOrphanedOrders(Duration threshold) {
        // Implementation would scan Redis for old order states
        // and mark them as orphaned in the database
        log.info("Scanning for orphaned orders older than {}", threshold);
    }
}
