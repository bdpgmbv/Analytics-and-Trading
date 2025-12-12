package com.vyshali.common.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "forward_contracts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ForwardContract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "forward_id")
    private Long forwardId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "counterparty_id")
    private Counterparty counterparty;

    @Column(name = "buy_currency", nullable = false, length = 3)
    private String buyCurrency;

    @Column(name = "sell_currency", nullable = false, length = 3)
    private String sellCurrency;

    @Column(name = "buy_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal buyAmount;

    @Column(name = "sell_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal sellAmount;

    @Column(name = "strike_rate", nullable = false, precision = 18, scale = 8)
    private BigDecimal strikeRate;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "value_date", nullable = false)
    private LocalDate valueDate;

    @Column(name = "days_to_maturity")
    private Integer daysToMaturity;

    @Column(name = "current_notional", precision = 18, scale = 4)
    private BigDecimal currentNotional;

    @Column(name = "unhedged_notional", precision = 18, scale = 4)
    private BigDecimal unhedgedNotional;

    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Status constants
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_MATURED = "MATURED";
    public static final String STATUS_ROLLED = "ROLLED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    // Business methods
    public boolean isActive() {
        return STATUS_ACTIVE.equals(status);
    }

    public boolean isMatured() {
        return STATUS_MATURED.equals(status);
    }

    /**
     * Calculate and update days to maturity
     */
    public void updateDaysToMaturity() {
        if (valueDate != null) {
            this.daysToMaturity = (int) ChronoUnit.DAYS.between(LocalDate.now(), valueDate);
        }
    }

    /**
     * Check if this forward is maturing soon (within 5 days)
     */
    public boolean isMaturingSoon() {
        updateDaysToMaturity();
        return daysToMaturity != null && daysToMaturity <= 5 && daysToMaturity >= 0;
    }

    /**
     * Check if this forward is overdue
     */
    public boolean isOverdue() {
        updateDaysToMaturity();
        return daysToMaturity != null && daysToMaturity < 0;
    }

    /**
     * Check if forward needs attention (maturing within 3 days or overdue)
     */
    public boolean needsAttention() {
        updateDaysToMaturity();
        return daysToMaturity != null && daysToMaturity <= 3;
    }

    /**
     * Get currency pair string
     */
    public String getCurrencyPair() {
        return buyCurrency + sellCurrency;
    }

    /**
     * Mark as rolled (new forward created)
     */
    public void markRolled() {
        this.status = STATUS_ROLLED;
    }

    /**
     * Mark as matured
     */
    public void markMatured() {
        this.status = STATUS_MATURED;
    }
}
