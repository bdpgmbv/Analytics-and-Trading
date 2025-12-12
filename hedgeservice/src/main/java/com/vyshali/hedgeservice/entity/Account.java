package com.vyshali.hedgeservice.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "Accounts")
public class Account {
    
    @Id
    @Column(name = "account_id")
    private Integer accountId;
    
    @Column(name = "account_number", length = 50)
    private String accountNumber;
    
    @Column(name = "account_type", length = 20)
    private String accountType;
}
