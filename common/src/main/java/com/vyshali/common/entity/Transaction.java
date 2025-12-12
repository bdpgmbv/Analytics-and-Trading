package com.vyshali.common.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transaction_id")
    private Long transactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "counterparty_id")
    private Counterparty counterparty;

    @Column(name = "source", nullable = false, length = 20)
    private String source; // MSPA, MANUAL

    @Column(name = "transaction_type", nullable = false, length = 20)
    private String transactionType; // BUY, SELL, ROLL_FORWARD

    @Column(name = "long_short", nullable = false, length = 5)
    private String longShort; // L, S

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "settle_date")
    private LocalDate settleDate;

    @Column(name = "quantity", nullable = false, precision = 18, scale = 4)
    private BigDecimal quantity;

    @Column(name = "price", precision = 18, scale = 6)
    private BigDecimal price;

    @Column(name = "cost_local", precision = 18, scale = 4)
    private BigDecimal costLocal;

    @Column(name = "cost_settle", precision = 18, scale = 4)
    private BigDecimal costSettle;

    @Column(name = "fx_rate", precision = 18, scale = 8)
    private BigDecimal fxRate;

    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = "PENDING";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Business methods
    public boolean isBuy() {
        return "BUY".equals(transactionType);
    }

    public boolean isSell() {
        return "SELL".equals(transactionType);
    }

    public boolean isLong() {
        return "L".equals(longShort);
    }

    public boolean isShort() {
        return "S".equals(longShort);
    }

    public boolean isPending() {
        return "PENDING".equals(status);
    }

    public boolean isSettled() {
        return "SETTLED".equals(status);
    }

    public boolean isToday() {
        return LocalDate.now().equals(tradeDate);
    }

    /**
     * Get the security description for display
     */
    public String getSecurityDescription() {
        return product != null ? product.getSecurityDescription() : null;
    }

    /**
     * Get the identifier for display
     */
    public String getIdentifier() {
        return product != null ? product.getIdentifier() : null;
    }

    /**
     * Get the identifier type
     */
    public String getIdentifierType() {
        return product != null ? product.getIdentifierType() : null;
    }
}
