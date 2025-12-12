package com.vyshali.common.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "share_classes",
       uniqueConstraints = @UniqueConstraint(columnNames = {"fund_id", "share_class_code"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShareClass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "share_class_id")
    private Long shareClassId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fund_id", nullable = false)
    private Fund fund;

    @Column(name = "share_class_code", nullable = false, length = 20)
    private String shareClassCode;

    @Column(name = "share_class_name", nullable = false, length = 100)
    private String shareClassName;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "nav", precision = 18, scale = 4)
    private BigDecimal nav;

    @Column(name = "performance_adjustment", precision = 18, scale = 4)
    @Builder.Default
    private BigDecimal performanceAdjustment = BigDecimal.ZERO;

    @Column(name = "subscription_redemption", precision = 18, scale = 4)
    @Builder.Default
    private BigDecimal subscriptionRedemption = BigDecimal.ZERO;

    @Column(name = "net_exposure", precision = 18, scale = 4)
    private BigDecimal netExposure;

    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Business methods
    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    /**
     * Calculate net exposure = NAV + Performance Adjustment + Sub/Red
     */
    public void calculateNetExposure() {
        BigDecimal total = BigDecimal.ZERO;
        if (nav != null) total = total.add(nav);
        if (performanceAdjustment != null) total = total.add(performanceAdjustment);
        if (subscriptionRedemption != null) total = total.add(subscriptionRedemption);
        this.netExposure = total;
    }

    /**
     * Check if this share class is in a different currency than fund base
     */
    public boolean isForeignCurrencyClass() {
        return fund != null && !fund.getBaseCurrency().equals(currency);
    }
}
