package com.vyshali.common.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "positions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Position {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "position_id")
    private Long positionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "snapshot_id", nullable = false)
    private Snapshot snapshot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "quantity", nullable = false, precision = 18, scale = 4)
    private BigDecimal quantity;

    @Column(name = "cost_basis_local", precision = 18, scale = 4)
    private BigDecimal costBasisLocal;

    @Column(name = "cost_basis_base", precision = 18, scale = 4)
    private BigDecimal costBasisBase;

    @Column(name = "market_value_local", precision = 18, scale = 4)
    private BigDecimal marketValueLocal;

    @Column(name = "market_value_base", precision = 18, scale = 4)
    private BigDecimal marketValueBase;

    @Column(name = "unrealized_pnl_local", precision = 18, scale = 4)
    private BigDecimal unrealizedPnlLocal;

    @Column(name = "unrealized_pnl_base", precision = 18, scale = 4)
    private BigDecimal unrealizedPnlBase;

    @Column(name = "price_used", precision = 18, scale = 6)
    private BigDecimal priceUsed;

    @Column(name = "fx_rate_used", precision = 18, scale = 8)
    private BigDecimal fxRateUsed;

    @Column(name = "position_type", length = 50)
    private String positionType;

    @Column(name = "source_system", length = 20)
    @Builder.Default
    private String sourceSystem = "MSPM";

    @Column(name = "is_excluded")
    @Builder.Default
    private Boolean isExcluded = false;

    @OneToMany(mappedBy = "position", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    private List<Exposure> exposures = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Business methods
    public boolean isLong() {
        return quantity != null && quantity.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isShort() {
        return quantity != null && quantity.compareTo(BigDecimal.ZERO) < 0;
    }

    /**
     * Get the currency exposure from the underlying product
     */
    public String getIssueCurrency() {
        return product != null ? product.getIssueCurrency() : null;
    }

    /**
     * Calculate unrealized P&L if not set
     */
    public void calculateUnrealizedPnl() {
        if (marketValueLocal != null && costBasisLocal != null) {
            this.unrealizedPnlLocal = marketValueLocal.subtract(costBasisLocal);
        }
        if (marketValueBase != null && costBasisBase != null) {
            this.unrealizedPnlBase = marketValueBase.subtract(costBasisBase);
        }
    }

    /**
     * Add an exposure entry
     */
    public void addExposure(Exposure exposure) {
        exposures.add(exposure);
        exposure.setPosition(this);
        exposure.setProduct(this.product);
    }

    /**
     * Check if position should be included in net exposure calculations
     */
    public boolean isIncludedInNetExposure() {
        return !Boolean.TRUE.equals(isExcluded);
    }

    /**
     * Get position sign (+1 for long, -1 for short)
     */
    public int getPositionSign() {
        if (quantity == null) return 0;
        return quantity.compareTo(BigDecimal.ZERO) >= 0 ? 1 : -1;
    }
}
