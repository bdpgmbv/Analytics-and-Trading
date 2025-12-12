package com.vyshali.hedgeservice.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "Transactions")
public class Transaction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transaction_id")
    private Long transactionId;
    
    @Column(name = "portfolio_id", nullable = false)
    private Integer portfolioId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", insertable = false, updatable = false)
    private Portfolio portfolio;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "counterparty_id")
    private Counterparty counterparty;
    
    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;
    
    @Column(name = "transaction_type", length = 50, nullable = false)
    private String transactionType;
    
    @Column(name = "currency", length = 3, nullable = false)
    private String currency;
    
    @Column(name = "quantity", precision = 18, scale = 4)
    private BigDecimal quantity;
    
    @Column(name = "price", precision = 18, scale = 6)
    private BigDecimal price;
    
    @Column(name = "amount", precision = 18, scale = 2)
    private BigDecimal amount;
    
    @Column(name = "amount_base", precision = 18, scale = 2)
    private BigDecimal amountBase;
    
    @Column(name = "fx_rate", precision = 18, scale = 6)
    private BigDecimal fxRate;
    
    @Column(name = "status", length = 20)
    private String status;
    
    @Column(name = "external_ref", length = 100)
    private String externalRef;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "executed_at")
    private LocalDateTime executedAt;
}
