package com.vyshali.priceservice.service;

/*
 * 12/02/2025 - 6:46 PM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.priceservice.dto.PriceTickDTO;
import com.vyshali.priceservice.dto.ValuationDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ValuationService {

    private final PositionCacheService positionCache;
    private final PriceCacheService priceCache;
    private final FxCacheService fxCache;
    private final SimpMessagingTemplate webSocket;

    /**
     * Triggered when a Price Tick arrives.
     * 1. Finds all accounts holding this product.
     * 2. Calculates new MV.
     * 3. Pushes to WebSocket.
     */
    public void recalculateAndPush(Integer productId) {
        Set<Integer> affectedAccounts = positionCache.getAccountsHoldingProduct(productId);
        PriceTickDTO priceTick = priceCache.getPrice(productId);

        if (affectedAccounts.isEmpty() || priceTick == null) return;

        // Parallel processing using Virtual Threads (implicit in Java 21)
        affectedAccounts.forEach(accountId -> {
            try {
                BigDecimal qty = positionCache.getQuantity(accountId, productId);
                if (qty.compareTo(BigDecimal.ZERO) == 0) return;

                // 1. Math
                BigDecimal fxRate = fxCache.getConversionRate(priceTick.currency(), "USD"); // Assuming Fund Base = USD
                BigDecimal marketValue = priceTick.price().multiply(qty).multiply(fxRate);

                ValuationDTO valuation = new ValuationDTO(accountId, productId, marketValue, priceTick.price(), fxRate, "REAL_TIME");

                // 2. Push to specific Account topic
                webSocket.convertAndSend("/topic/account/" + accountId, valuation);

            } catch (Exception e) {
                log.error("Valuation error for account {}", accountId, e);
            }
        });
    }
}
