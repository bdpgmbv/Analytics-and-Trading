package com.vyshali.common.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "fx_rates",
       uniqueConstraints = @UniqueConstraint(columnNames = {"currency_pair", "rate_date", "source"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FxRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fx_rate_id")
    private Long fxRateId;

    @Column(name = "currency_pair", nullable = false, length = 7)
    private String currencyPair;

    @Column(name = "base_currency", nullable = false, length = 3)
    private String baseCurrency;

    @Column(name = "quote_currency", nullable = false, length = 3)
    private String quoteCurrency;

    @Column(name = "rate_date", nullable = false)
    private LocalDate rateDate;

    @Column(name = "rate_time")
    private LocalDateTime rateTime;

    @Column(name = "mid_rate", nullable = false, precision = 18, scale = 8)
    private BigDecimal midRate;

    @Column(name = "bid_rate", precision = 18, scale = 8)
    private BigDecimal bidRate;

    @Column(name = "ask_rate", precision = 18, scale = 8)
    private BigDecimal askRate;

    @Column(name = "forward_points_1m", precision = 18, scale = 8)
    private BigDecimal forwardPoints1m;

    @Column(name = "forward_points_3m", precision = 18, scale = 8)
    private BigDecimal forwardPoints3m;

    @Column(name = "source", nullable = false, length = 20)
    private String source;

    @Column(name = "is_stale")
    @Builder.Default
    private Boolean isStale = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Business methods
    public boolean isStale() {
        return Boolean.TRUE.equals(isStale);
    }

    /**
     * Get the spread in pips
     */
    public BigDecimal getSpreadPips() {
        if (bidRate != null && askRate != null) {
            BigDecimal spread = askRate.subtract(bidRate);
            // For JPY pairs, multiply by 100; for others by 10000
            int multiplier = quoteCurrency.equals("JPY") ? 100 : 10000;
            return spread.multiply(BigDecimal.valueOf(multiplier));
        }
        return null;
    }

    /**
     * Get 1-month forward rate
     */
    public BigDecimal getForwardRate1m() {
        if (midRate != null && forwardPoints1m != null) {
            return midRate.add(forwardPoints1m);
        }
        return midRate;
    }

    /**
     * Get 3-month forward rate
     */
    public BigDecimal getForwardRate3m() {
        if (midRate != null && forwardPoints3m != null) {
            return midRate.add(forwardPoints3m);
        }
        return midRate;
    }

    /**
     * Get the inverse rate
     */
    public BigDecimal getInverseRate() {
        if (midRate != null && midRate.compareTo(BigDecimal.ZERO) > 0) {
            return BigDecimal.ONE.divide(midRate, 8, BigDecimal.ROUND_HALF_UP);
        }
        return null;
    }

    /**
     * Create a currency pair string from two currencies
     */
    public static String createPair(String base, String quote) {
        return base + quote;
    }

    /**
     * Check if this is a major currency pair (G10)
     */
    public boolean isMajorPair() {
        String[] majors = {"EUR", "USD", "GBP", "JPY", "CHF", "AUD", "CAD", "NZD"};
        for (String major : majors) {
            if (baseCurrency.equals(major) || quoteCurrency.equals(major)) {
                return true;
            }
        }
        return false;
    }
}
