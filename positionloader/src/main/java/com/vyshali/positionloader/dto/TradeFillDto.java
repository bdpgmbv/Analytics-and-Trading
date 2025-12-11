package com.vyshali.positionloader.dto;

import java.time.Instant;

/**
 * Trade Fill Data Transfer Object.
 * Represents a single execution/fill from the trading system.
 */
public record TradeFillDto(
    String fillId,
    String orderId,
    String symbol,
    String side,        // BUY or SELL
    double quantity,
    double price,
    Instant timestamp,
    String venue,
    String status
) {
    
    /**
     * Compact constructor with defaults.
     */
    public TradeFillDto {
        if (timestamp == null) timestamp = Instant.now();
        if (venue == null) venue = "UNKNOWN";
        if (status == null) status = "NEW";
    }
    
    /**
     * Simplified constructor.
     */
    public static TradeFillDto of(String orderId, String symbol, String side, 
            double quantity, double price) {
        return new TradeFillDto(
            null,
            orderId,
            symbol,
            side,
            quantity,
            price,
            Instant.now(),
            "UNKNOWN",
            "NEW"
        );
    }
    
    /**
     * Check if this is a buy order.
     */
    public boolean isBuy() {
        return "BUY".equalsIgnoreCase(side);
    }
    
    /**
     * Check if this is a sell order.
     */
    public boolean isSell() {
        return "SELL".equalsIgnoreCase(side);
    }
    
    /**
     * Get signed quantity (negative for sells).
     */
    public double signedQuantity() {
        return isSell() ? -quantity : quantity;
    }
}
