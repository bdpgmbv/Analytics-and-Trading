package com.vyshali.common.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "price_overrides")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "override_id")
    private Long overrideId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "currency_pair", length = 7)
    private String currencyPair;

    @Column(name = "override_type", nullable = false, length = 20)
    private String overrideType; // PRICE, FX_RATE

    @Column(name = "override_value", nullable = false, precision = 18, scale = 8)
    private BigDecimal overrideValue;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "override_reason", length = 255)
    private String overrideReason;

    @Column(name = "created_by", length = 50)
    private String createdBy;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Override type constants
    public static final String TYPE_PRICE = "PRICE";
    public static final String TYPE_FX_RATE = "FX_RATE";

    // Business methods
    public boolean isActive() {
        return Boolean.TRUE.equals(isActive);
    }

    public boolean isPriceOverride() {
        return TYPE_PRICE.equals(overrideType);
    }

    public boolean isFxRateOverride() {
        return TYPE_FX_RATE.equals(overrideType);
    }

    /**
     * Check if override is effective for a given date
     */
    public boolean isEffectiveOn(LocalDate date) {
        if (!isActive()) return false;
        if (effectiveDate.isAfter(date)) return false;
        if (expiryDate != null && expiryDate.isBefore(date)) return false;
        return true;
    }

    /**
     * Check if override is currently effective
     */
    public boolean isCurrentlyEffective() {
        return isEffectiveOn(LocalDate.now());
    }

    /**
     * Deactivate the override
     */
    public void deactivate() {
        this.isActive = false;
    }

    /**
     * Check if this is an account-specific override
     */
    public boolean isAccountSpecific() {
        return account != null;
    }

    /**
     * Check if this is a global override (no specific account)
     */
    public boolean isGlobalOverride() {
        return account == null;
    }
}
