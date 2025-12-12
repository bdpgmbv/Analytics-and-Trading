package com.vyshali.common.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "cash_balances",
       uniqueConstraints = @UniqueConstraint(columnNames = {"account_id", "currency", "balance_date"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CashBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cash_balance_id")
    private Long cashBalanceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "balance_date", nullable = false)
    private LocalDate balanceDate;

    @Column(name = "cash_balance", nullable = false, precision = 18, scale = 4)
    private BigDecimal cashBalance;

    @Column(name = "unhedged_exposure", precision = 18, scale = 4)
    private BigDecimal unhedgedExposure;

    @Column(name = "trade_type", length = 20)
    private String tradeType; // SPOT, FORWARD, SPOT_AND_FORWARD

    @Column(name = "spot_amount", precision = 18, scale = 4)
    private BigDecimal spotAmount;

    @Column(name = "forward_amount", precision = 18, scale = 4)
    private BigDecimal forwardAmount;

    @Column(name = "value_date")
    private LocalDate valueDate;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Business methods
    public boolean hasUnhedgedExposure() {
        return unhedgedExposure != null && unhedgedExposure.compareTo(BigDecimal.ZERO) != 0;
    }

    public boolean isPositiveBalance() {
        return cashBalance != null && cashBalance.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isNegativeBalance() {
        return cashBalance != null && cashBalance.compareTo(BigDecimal.ZERO) < 0;
    }

    /**
     * Get the base currency from account's fund
     */
    public String getBaseCurrency() {
        return account != null ? account.getBaseCurrency() : null;
    }

    /**
     * Check if this cash is in a foreign currency (vs base)
     */
    public boolean isForeignCurrency() {
        String baseCurrency = getBaseCurrency();
        return baseCurrency != null && !baseCurrency.equals(currency);
    }
}
