package com.vyshali.hedgeservice.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "Funds")
public class Fund {
    
    @Id
    @Column(name = "fund_id")
    private Integer fundId;
    
    @Column(name = "fund_name", length = 100)
    private String fundName;
    
    @Column(name = "base_currency", length = 3)
    private String baseCurrency;
}
