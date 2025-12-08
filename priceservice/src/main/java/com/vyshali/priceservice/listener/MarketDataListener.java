package com.vyshali.priceservice.listener;

/*
 * 12/02/2025 - 6:48 PM
 * @author Vyshali Prabananth Lal
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyshali.priceservice.dto.PriceTickDTO;
import com.vyshali.priceservice.service.PriceCacheService;
import com.vyshali.priceservice.service.PricePersistenceService;
import com.vyshali.priceservice.service.SymbologyService;
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
    private final PricePersistenceService persistenceService;
    private final ValuationService valuationService;
    private final SymbologyService symbologyService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "MARKET_DATA_TICKS", groupId = "prices-group", containerFactory = "batchFactory")
    public void onPriceBatch(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
        for (var record : records) {
            try {
                PriceTickDTO tick = objectMapper.readValue(record.value(), PriceTickDTO.class);

                // 1. SYMBOLOGY RESOLUTION (Using Optional)
                Integer internalId = tick.productId();

                if (tick.ticker() != null) {
                    internalId = symbologyService.resolveTicker(tick.ticker())
                            .orElse(tick.productId()); // Fallback to raw ID if resolution fails
                }

                // 2. Update State
                priceCache.updatePrice(tick);

                // 3. Queue for DB
                persistenceService.markDirty(internalId);

                // 4. Trigger Calculation
                valuationService.recalculateAndPush(internalId);

            } catch (Exception e) {
                log.error("Price processing error", e);
            }
        }
        ack.acknowledge();
    }
}