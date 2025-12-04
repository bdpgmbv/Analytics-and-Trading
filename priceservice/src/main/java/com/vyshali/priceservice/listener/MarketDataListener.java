package com.vyshali.priceservice.listener;

/*
 * 12/02/2025 - 6:48 PM
 * @author Vyshali Prabananth Lal
 */
/*
 * Updated to use Symbology Service for ID Resolution
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyshali.priceservice.dto.PriceTickDTO;
import com.vyshali.priceservice.service.PriceCacheService;
import com.vyshali.priceservice.service.PricePersistenceService;
import com.vyshali.priceservice.service.SymbologyService; // <--- NEW IMPORT
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
    private final SymbologyService symbologyService; // <--- INJECTED
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "MARKET_DATA_TICKS", groupId = "prices-group", containerFactory = "batchFactory")
    public void onPriceBatch(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
        for (var record : records) {
            try {
                PriceTickDTO tick = objectMapper.readValue(record.value(), PriceTickDTO.class);

                // 1. SYMBOLOGY RESOLUTION (The "Bank" Way)
                // Market Data feeds usually provide a Ticker/ISIN. We need the Internal ID.
                Integer internalId = tick.productId(); // Default if logic fails

                if (tick.ticker() != null) {
                    Integer resolvedId = symbologyService.resolveTicker(tick.ticker());
                    if (resolvedId != null) {
                        internalId = resolvedId;
                    }
                }

                // 2. Update State (Redis) using the Stable ID
                // Note: You might need to reconstruct the DTO with the resolved ID if downstream needs it.
                // For now, we update cache with existing DTO but notify valuation logic using internalId.
                priceCache.updatePrice(tick);

                // 3. Queue for DB (Throttled)
                persistenceService.markDirty(internalId);

                // 4. Trigger Server-Side Calculation (Reverse Index -> Math -> Conflation)
                valuationService.recalculateAndPush(internalId);

            } catch (Exception e) {
                log.error("Price processing error", e);
            }
        }
        ack.acknowledge();
    }
}