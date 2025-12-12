package com.vyshali.hedgeservice.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "ForwardContracts")
public class ForwardContract {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "forward_contract_id")
    private Long forwardContractId;
    
    @Column(name = "portfolio_id", nullable = false)
    private Integer portfolioId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", insertable = false, updatable = false)
    private Portfolio portfolio;
    
    @Column(name = "trade_execution_id")
    private Long tradeExecutionId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "counterparty_id")
    private Counterparty counterparty;
    
    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;
    
    @Column(name = "maturity_date", nullable = false)
    private LocalDate maturityDate;
    
    @Column(name = "buy_currency", length = 3, nullable = false)
    private String buyCurrency;
    
    @Column(name = "sell_currency", length = 3, nullable = false)
    private String sellCurrency;
    
    @Column(name = "notional_amount", precision = 18, scale = 2, nullable = false)
    private BigDecimal notionalAmount;
    
    @Column(name = "forward_rate", precision = 18, scale = 6, nullable = false)
    private BigDecimal forwardRate;
    
    @Column(name = "status", length = 20)
    private String status;
    
    @Column(name = "external_ref", length = 100)
    private String externalRef;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
