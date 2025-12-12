package com.vyshali.common.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "exposures",
       uniqueConstraints = @UniqueConstraint(columnNames = {"position_id", "exposure_type", "currency"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Exposure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "exposure_id")
    private Long exposureId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "position_id", nullable = false)
    private Position position;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "exposure_type", nullable = false, length = 20)
    private String exposureType; // GENERIC, SPECIFIC

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "weight_percent", nullable = false, precision = 8, scale = 4)
    private BigDecimal weightPercent;

    @Column(name = "exposure_amount_local", precision = 18, scale = 4)
    private BigDecimal exposureAmountLocal;

    @Column(name = "exposure_amount_base", precision = 18, scale = 4)
    private BigDecimal exposureAmountBase;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Business methods
    public boolean isGeneric() {
        return "GENERIC".equals(exposureType);
    }

    public boolean isSpecific() {
        return "SPECIFIC".equals(exposureType);
    }

    /**
     * Calculate exposure amount based on position market value and weight
     */
    public void calculateExposureAmounts(BigDecimal positionMvLocal, BigDecimal positionMvBase) {
        if (weightPercent != null) {
            BigDecimal weightDecimal = weightPercent.divide(BigDecimal.valueOf(100), 6, BigDecimal.ROUND_HALF_UP);
            
            if (positionMvLocal != null) {
                this.exposureAmountLocal = positionMvLocal.multiply(weightDecimal);
            }
            if (positionMvBase != null) {
                this.exposureAmountBase = positionMvBase.multiply(weightDecimal);
            }
        }
    }

    /**
     * Create a simple 100% exposure for a single currency
     */
    public static Exposure createFullExposure(String exposureType, String currency) {
        return Exposure.builder()
            .exposureType(exposureType)
            .currency(currency)
            .weightPercent(BigDecimal.valueOf(100))
            .build();
    }
}
