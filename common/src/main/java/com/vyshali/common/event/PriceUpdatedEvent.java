package com.vyshali.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Event published when a price is updated
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceUpdatedEvent {

    private Long productId;
    private String identifier;
    private String ticker;
    private BigDecimal price;
    private BigDecimal previousPrice;
    private String source;
    private LocalDateTime updatedAt;
    private Boolean isStale;
    
    /**
     * Kafka topic for this event
     */
    public static final String TOPIC = "fxanalyzer.prices.updated";
    
    /**
     * Calculate price change percentage
     */
    public BigDecimal getPriceChangePercent() {
        if (previousPrice == null || previousPrice.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return price.subtract(previousPrice)
                .divide(previousPrice, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }
}
