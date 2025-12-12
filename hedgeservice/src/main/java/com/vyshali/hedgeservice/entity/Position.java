package com.vyshali.hedgeservice.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "Positions")
public class Position {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "position_id")
    private Long positionId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "snapshot_id", nullable = false)
    private Snapshot snapshot;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;
    
    @Column(name = "quantity", precision = 18, scale = 4, nullable = false)
    private BigDecimal quantity;
    
    @Column(name = "price", precision = 18, scale = 6)
    private BigDecimal price;
    
    @Column(name = "market_value", precision = 18, scale = 2)
    private BigDecimal marketValue;
    
    @Column(name = "market_value_base", precision = 18, scale = 2)
    private BigDecimal marketValueBase;
    
    @Column(name = "cost_basis", precision = 18, scale = 2)
    private BigDecimal costBasis;
    
    @Column(name = "unrealized_pnl", precision = 18, scale = 2)
    private BigDecimal unrealizedPnl;
    
    @Column(name = "realized_pnl", precision = 18, scale = 2)
    private BigDecimal realizedPnl;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
