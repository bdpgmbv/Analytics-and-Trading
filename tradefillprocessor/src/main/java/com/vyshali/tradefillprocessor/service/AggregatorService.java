package com.vyshali.tradefillprocessor.service;

/*
 * 12/03/2025 - 1:14 PM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.tradefillprocessor.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AggregatorService {

    private final RedisTemplate<String, OrderStateDTO> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final TradePersistenceService persistenceService;

    // Output Topic
    private static final String DOWNSTREAM_TOPIC = "MSPA_INTRADAY";

    public void processExecution(ExecutionReportDTO fill) {
        log.info("Processing Fill: {} Qty: {} @ {}", fill.execId(), fill.lastQty(), fill.lastPx());

        // 1. Audit Trail (DB)
        persistenceService.saveFill(fill);

        // 2. Load/Init State (Redis)
        String cacheKey = "ORDER:" + fill.orderId();
        OrderStateDTO state = redisTemplate.opsForValue().get(cacheKey);

        if (state == null) {
            state = new OrderStateDTO(fill.orderId(), fill.accountId(), fill.ticker(), fill.side(), BigDecimal.ZERO, BigDecimal.ZERO, "NEW");
        }

        // 3. Aggregate (VWAP Calculation)
        BigDecimal newTotalQty = state.totalFilledQty().add(fill.lastQty());
        BigDecimal fillNotional = fill.lastQty().multiply(fill.lastPx());
        BigDecimal newTotalNotional = state.totalNotional().add(fillNotional);

        // 4. Update State
        OrderStateDTO newState = new OrderStateDTO(state.orderId(), state.accountId(), state.ticker(), state.side(), newTotalQty, newTotalNotional, fill.status());

        redisTemplate.opsForValue().set(cacheKey, newState);
        persistenceService.updateOrderSummary(newState);

        // 5. Completion Logic
        if ("FILLED".equalsIgnoreCase(fill.status())) {
            publishToDownstream(newState);
            redisTemplate.delete(cacheKey); // Clear cache once done
            log.info("Order {} Complete. Finalized to PositionLoader.", fill.orderId());
        }
    }

    private void publishToDownstream(OrderStateDTO state) {
        // Final Avg Price
        BigDecimal avgPrice = (state.totalFilledQty().compareTo(BigDecimal.ZERO) == 0) ? BigDecimal.ZERO : state.totalNotional().divide(state.totalFilledQty(), 6, RoundingMode.HALF_UP);

        // Map to PositionLoader contract
        TradeEventDTO.PositionDetail posDetail = new TradeEventDTO.PositionDetail(null, // Loader will lookup ID
                state.ticker(), state.totalFilledQty(), state.side(), avgPrice);

        TradeEventDTO event = new TradeEventDTO(state.accountId(), 100, // Placeholder Client ID
                List.of(posDetail));

        kafkaTemplate.send(DOWNSTREAM_TOPIC, state.accountId().toString(), event);
    }
}
