package com.vyshali.hedgeservice.listener;

/*
 * 12/08/2025 - 2:43 PM
 * @author Vyshali Prabananth Lal
 */

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.cache.CacheManager;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class HedgePositionListener {

    private final CacheManager cacheManager;

    // Listens to the EXACT same topic that Loader sends to Price Service
    @KafkaListener(topics = "POSITION_CHANGE_EVENTS", groupId = "hedge-svc-group")
    public void onPositionChange(ConsumerRecord<String, String> record) {
        String accountIdString = record.key();
        try {
            Integer accountId = Integer.parseInt(accountIdString);

            // INVALIDATION: "The database changed, so the cache is dirty."
            // This forces the next UI refresh to hit the DB and get the new Grid.
            if (cacheManager.getCache("hedgePositions") != null) {
                cacheManager.getCache("hedgePositions").evict(accountId);
                log.info("Invalidated Hedge Grid Cache for Account {}", accountId);
            }

        } catch (NumberFormatException e) {
            log.warn("Received invalid account ID in position change event: {}", accountIdString);
        }
    }
}
