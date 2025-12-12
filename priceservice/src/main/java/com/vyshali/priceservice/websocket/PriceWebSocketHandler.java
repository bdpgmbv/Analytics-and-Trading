package com.vyshali.priceservice.websocket;

import com.vyshali.fxanalyzer.common.dto.FxRateDto;
import com.vyshali.fxanalyzer.common.dto.PriceDto;
import com.vyshali.fxanalyzer.priceservice.service.FxRateService;
import com.vyshali.fxanalyzer.priceservice.service.PriceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

/**
 * WebSocket handler for real-time price subscriptions.
 * 
 * Subscribe endpoints:
 * - /topic/prices/{productId} - subscribe to specific security
 * - /topic/prices/all - subscribe to all price updates
 * - /topic/fx-rates/{currencyPair} - subscribe to specific FX rate
 * - /topic/fx-rates/all - subscribe to all FX rate updates
 * 
 * Request endpoints:
 * - /app/price/{productId} - request current price
 * - /app/fx-rate/{currencyPair} - request current FX rate
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class PriceWebSocketHandler {

    private final PriceService priceService;
    private final FxRateService fxRateService;

    /**
     * Handle subscription to specific product price.
     * Returns current price immediately upon subscription.
     */
    @SubscribeMapping("/prices/{productId}")
    public PriceDto subscribeToPrice(@DestinationVariable Long productId) {
        log.debug("Client subscribed to price for product {}", productId);
        try {
            return priceService.getPrice(productId);
        } catch (Exception e) {
            log.warn("Could not get initial price for subscription: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Handle subscription to specific FX rate.
     * Returns current rate immediately upon subscription.
     */
    @SubscribeMapping("/fx-rates/{currencyPair}")
    public FxRateDto subscribeToFxRate(@DestinationVariable String currencyPair) {
        log.debug("Client subscribed to FX rate for {}", currencyPair);
        try {
            return fxRateService.getFxRate(currencyPair);
        } catch (Exception e) {
            log.warn("Could not get initial FX rate for subscription: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Request current price via WebSocket.
     */
    @MessageMapping("/price/{productId}")
    @SendTo("/topic/prices/{productId}")
    public PriceDto requestPrice(@DestinationVariable Long productId) {
        log.debug("Price request received for product {}", productId);
        return priceService.getPrice(productId);
    }

    /**
     * Request current FX rate via WebSocket.
     */
    @MessageMapping("/fx-rate/{currencyPair}")
    @SendTo("/topic/fx-rates/{currencyPair}")
    public FxRateDto requestFxRate(@DestinationVariable String currencyPair) {
        log.debug("FX rate request received for {}", currencyPair);
        return fxRateService.getFxRate(currencyPair);
    }
}
