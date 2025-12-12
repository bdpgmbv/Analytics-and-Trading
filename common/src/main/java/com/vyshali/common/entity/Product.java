package com.vyshali.common.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "products", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"identifier_type", "identifier"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "identifier_type", nullable = false, length = 20)
    private String identifierType;

    @Column(name = "identifier", nullable = false, length = 50)
    private String identifier;

    @Column(name = "ticker", length = 20)
    private String ticker;

    @Column(name = "security_description", nullable = false, length = 255)
    private String securityDescription;

    @Column(name = "asset_class", nullable = false, length = 50)
    private String assetClass;

    @Column(name = "issue_currency", nullable = false, length = 3)
    private String issueCurrency;

    @Column(name = "settlement_currency", nullable = false, length = 3)
    private String settlementCurrency;

    @Column(name = "risk_region", length = 50)
    private String riskRegion;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Business methods
    public boolean isEquity() {
        return "EQUITY".equals(assetClass);
    }

    public boolean isEquitySwap() {
        return "EQUITY_SWAP".equals(assetClass);
    }

    public boolean isFxForward() {
        return "FX_FORWARD".equals(assetClass);
    }

    public boolean isFxSpot() {
        return "FX_SPOT".equals(assetClass);
    }

    public boolean isCash() {
        return "CASH".equals(assetClass);
    }

    public boolean isBond() {
        return "BOND".equals(assetClass);
    }

    /**
     * Determines if this product has FX risk (issue currency != settlement currency)
     */
    public boolean hasFxRisk() {
        return !issueCurrency.equals(settlementCurrency);
    }

    /**
     * Returns the currency that creates FX exposure
     */
    public String getRiskCurrency() {
        return issueCurrency;
    }

    /**
     * Check if this is a G10 currency product
     */
    public boolean isG10() {
        return "G10".equals(riskRegion);
    }

    /**
     * Check if this is an emerging market product
     */
    public boolean isEmergingMarket() {
        return riskRegion != null && riskRegion.startsWith("EM_");
    }

    /**
     * Get display name for UI
     */
    public String getDisplayName() {
        if (ticker != null && !ticker.isEmpty()) {
            return ticker + " - " + securityDescription;
        }
        return identifier + " - " + securityDescription;
    }
}
