package com.vyshali.hedgeservice.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "CashBalances")
public class CashBalance {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cash_balance_id")
    private Long cashBalanceId;
    
    @Column(name = "portfolio_id", nullable = false)
    private Integer portfolioId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", insertable = false, updatable = false)
    private Portfolio portfolio;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;
    
    @Column(name = "currency", length = 3, nullable = false)
    private String currency;
    
    @Column(name = "as_of_date", nullable = false)
    private LocalDate asOfDate;
    
    @Column(name = "balance", precision = 18, scale = 2, nullable = false)
    private BigDecimal balance;
    
    @Column(name = "balance_base", precision = 18, scale = 2)
    private BigDecimal balanceBase;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
