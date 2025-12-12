package com.vyshali.hedgeservice.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "Products")
public class Product {
    
    @Id
    @Column(name = "product_id")
    private Integer productId;
    
    @Column(name = "ticker", length = 20)
    private String ticker;
    
    @Column(name = "security_description", length = 255)
    private String securityDescription;
    
    @Column(name = "currency", length = 3)
    private String currency;
    
    @Column(name = "asset_class", length = 50)
    private String assetClass;
}
