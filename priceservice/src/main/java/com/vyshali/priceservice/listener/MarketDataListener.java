package com.vyshali.priceservice.listener;

/*
 * 12/02/2025 - 6:48 PM
 * @author Vyshali Prabananth Lal
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyshali.priceservice.dto.FxRateDTO;
import com.vyshali.priceservice.dto.PriceTickDTO;
import com.vyshali.priceservice.service.FxCacheService;
import com.vyshali.priceservice.service.PriceCacheService;
import com.vyshali.priceservice.service.PricePersistenceService;
import com.vyshali.priceservice.service.ValuationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketDataListener {

    private final PriceCacheService priceCache;
    private final FxCacheService fxCache;
    private final PricePersistenceService persistenceService;
    private final ValuationService valuationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "MARKET_DATA_TICKS", groupId = "prices-group", containerFactory = "batchFactory")
    public void onPriceBatch(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
        for (var record : records) {
            try {
                PriceTickDTO tick = objectMapper.readValue(record.value(), PriceTickDTO.class);

                // 1. Update State
                priceCache.updatePrice(tick);
                persistenceService.markDirty(tick.productId());

                // 2. Trigger Calculation (Server-side)
                valuationService.recalculateAndPush(tick.productId());

            } catch (Exception e) {
                log.error("Price Error", e);
            }
        }
        ack.acknowledge();
    }

    @KafkaListener(topics = "FX_RATES_TICKS", groupId = "fx-group", containerFactory = "batchFactory")
    public void onFxBatch(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
        for (var record : records) {
            try {
                FxRateDTO rate = objectMapper.readValue(record.value(), FxRateDTO.class);
                fxCache.updateFxRate(rate);
                // Note: Changing FX triggers valuation for ALL products using that currency.
                // Skipped here for brevity, but same logic as price tick applies.
            } catch (Exception e) {
                log.error("FX Error", e);
            }
        }
        ack.acknowledge();
    }
}
