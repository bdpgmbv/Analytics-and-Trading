package com.vyshali.common.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Price entity with source hierarchy support.
 * 
 * Price priority (lower number = higher priority):
 * 1 = OVERRIDE (user provided)
 * 2 = REALTIME (Filter pricing, 20min delayed)
 * 3 = RCP_SNAP (EOD for closed markets)
 * 4 = MSPA (EOD from MSPA)
 */
@Entity
@Table(name = "prices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Price {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "price_id")
    private Long priceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "price_date", nullable = false)
    private LocalDate priceDate;

    @Column(name = "price_time")
    private LocalDateTime priceTime;

    @Column(name = "price_value", nullable = false, precision = 18, scale = 6)
    private BigDecimal priceValue;

    @Column(name = "bid_price", precision = 18, scale = 6)
    private BigDecimal bidPrice;

    @Column(name = "ask_price", precision = 18, scale = 6)
    private BigDecimal askPrice;

    @Column(name = "source", nullable = false, length = 20)
    private String source;

    @Column(name = "source_priority", nullable = false)
    private Integer sourcePriority;

    @Column(name = "is_stale")
    @Builder.Default
    private Boolean isStale = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Source constants
    public static final String SOURCE_OVERRIDE = "OVERRIDE";
    public static final String SOURCE_REALTIME = "REALTIME";
    public static final String SOURCE_RCP_SNAP = "RCP_SNAP";
    public static final String SOURCE_MSPA = "MSPA";

    // Priority constants
    public static final int PRIORITY_OVERRIDE = 1;
    public static final int PRIORITY_REALTIME = 2;
    public static final int PRIORITY_RCP_SNAP = 3;
    public static final int PRIORITY_MSPA = 4;

    // Business methods
    public boolean isOverride() {
        return SOURCE_OVERRIDE.equals(source);
    }

    public boolean isRealtime() {
        return SOURCE_REALTIME.equals(source);
    }

    public boolean isStale() {
        return Boolean.TRUE.equals(isStale);
    }

    /**
     * Get the mid price (average of bid/ask) or price value
     */
    public BigDecimal getMidPrice() {
        if (bidPrice != null && askPrice != null) {
            return bidPrice.add(askPrice).divide(BigDecimal.valueOf(2), 6, BigDecimal.ROUND_HALF_UP);
        }
        return priceValue;
    }

    /**
     * Get the spread in basis points
     */
    public BigDecimal getSpreadBps() {
        if (bidPrice != null && askPrice != null && bidPrice.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal spread = askPrice.subtract(bidPrice);
            BigDecimal mid = getMidPrice();
            return spread.divide(mid, 6, BigDecimal.ROUND_HALF_UP)
                         .multiply(BigDecimal.valueOf(10000));
        }
        return null;
    }

    /**
     * Create an override price
     */
    public static Price createOverride(Product product, LocalDate date, BigDecimal value) {
        return Price.builder()
            .product(product)
            .priceDate(date)
            .priceTime(LocalDateTime.now())
            .priceValue(value)
            .source(SOURCE_OVERRIDE)
            .sourcePriority(PRIORITY_OVERRIDE)
            .isStale(false)
            .build();
    }

    /**
     * Get priority for a given source
     */
    public static int getPriorityForSource(String source) {
        return switch (source) {
            case SOURCE_OVERRIDE -> PRIORITY_OVERRIDE;
            case SOURCE_REALTIME -> PRIORITY_REALTIME;
            case SOURCE_RCP_SNAP -> PRIORITY_RCP_SNAP;
            case SOURCE_MSPA -> PRIORITY_MSPA;
            default -> 99;
        };
    }
}
