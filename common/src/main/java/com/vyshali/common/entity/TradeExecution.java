package com.vyshali.common.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * TradeExecution entity - tracks FX trades sent to and executed via FX Matrix.
 * 
 * SOLVES ISSUE #2: "No tracking for executed trades (sent vs executed)"
 * 
 * This entity provides a complete audit trail of:
 * - When a trade was SENT to FX Matrix
 * - When it was EXECUTED (or REJECTED/FAILED)
 * - Full details of the execution
 */
@Entity
@Table(name = "trade_executions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradeExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "execution_id")
    private Long executionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "counterparty_id")
    private Counterparty counterparty;

    @Column(name = "execution_ref", nullable = false, unique = true, length = 50)
    private String executionRef;

    @Column(name = "trade_type", nullable = false, length = 20)
    private String tradeType; // SPOT, FORWARD, SWAP

    @Column(name = "buy_currency", nullable = false, length = 3)
    private String buyCurrency;

    @Column(name = "sell_currency", nullable = false, length = 3)
    private String sellCurrency;

    @Column(name = "buy_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal buyAmount;

    @Column(name = "sell_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal sellAmount;

    @Column(name = "execution_rate", nullable = false, precision = 18, scale = 8)
    private BigDecimal executionRate;

    @Column(name = "value_date", nullable = false)
    private LocalDate valueDate;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    @Column(name = "executed_at")
    private LocalDateTime executedAt;

    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = "SENT";

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "source_tab", length = 30)
    private String sourceTab;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Status constants
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_EXECUTED = "EXECUTED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_FAILED = "FAILED";

    // Trade type constants
    public static final String TYPE_SPOT = "SPOT";
    public static final String TYPE_FORWARD = "FORWARD";
    public static final String TYPE_SWAP = "SWAP";

    // Source tab constants
    public static final String TAB_SECURITY_EXPOSURE = "SECURITY_EXPOSURE";
    public static final String TAB_CASH_MANAGEMENT = "CASH_MANAGEMENT";
    public static final String TAB_FORWARD_MATURITY = "FORWARD_MATURITY";
    public static final String TAB_SHARE_CLASS = "SHARE_CLASS";

    // Business methods
    public boolean isSent() {
        return STATUS_SENT.equals(status);
    }

    public boolean isExecuted() {
        return STATUS_EXECUTED.equals(status);
    }

    public boolean isRejected() {
        return STATUS_REJECTED.equals(status);
    }

    public boolean isFailed() {
        return STATUS_FAILED.equals(status);
    }

    public boolean isPending() {
        return STATUS_SENT.equals(status);
    }

    public boolean isCompleted() {
        return isExecuted() || isRejected() || isFailed();
    }

    /**
     * Mark this execution as completed successfully
     */
    public void markExecuted() {
        this.status = STATUS_EXECUTED;
        this.executedAt = LocalDateTime.now();
    }

    /**
     * Mark this execution as rejected by FX Matrix
     */
    public void markRejected(String reason) {
        this.status = STATUS_REJECTED;
        this.executedAt = LocalDateTime.now();
        this.errorMessage = reason;
    }

    /**
     * Mark this execution as failed (system error)
     */
    public void markFailed(String error) {
        this.status = STATUS_FAILED;
        this.executedAt = LocalDateTime.now();
        this.errorMessage = error;
    }

    /**
     * Get the currency pair string (e.g., "EURUSD")
     */
    public String getCurrencyPair() {
        return buyCurrency + sellCurrency;
    }

    /**
     * Get latency in milliseconds between sent and executed
     */
    public Long getExecutionLatencyMs() {
        if (sentAt != null && executedAt != null) {
            return java.time.Duration.between(sentAt, executedAt).toMillis();
        }
        return null;
    }

    /**
     * Check if execution is taking too long (over 30 seconds)
     */
    public boolean isStale() {
        if (isSent() && sentAt != null) {
            return java.time.Duration.between(sentAt, LocalDateTime.now()).getSeconds() > 30;
        }
        return false;
    }
}
