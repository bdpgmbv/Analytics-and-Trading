package com.vyshali.hedgeservice.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "Portfolios")
public class Portfolio {
    
    @Id
    @Column(name = "portfolio_id")
    private Integer portfolioId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fund_id")
    private Fund fund;
}
