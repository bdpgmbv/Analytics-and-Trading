package com.vyshali.hedgeservice.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "Counterparties")
public class Counterparty {
    
    @Id
    @Column(name = "counterparty_id")
    private Integer counterpartyId;
    
    @Column(name = "counterparty_name", length = 100)
    private String counterpartyName;
}
