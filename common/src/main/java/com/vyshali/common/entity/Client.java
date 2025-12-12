package com.vyshali.common.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "clients")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "client_id")
    private Long clientId;

    @Column(name = "client_code", nullable = false, unique = true, length = 20)
    private String clientCode;

    @Column(name = "client_name", nullable = false, length = 100)
    private String clientName;

    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    @OneToMany(mappedBy = "client", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Fund> funds = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Business methods
    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    public void addFund(Fund fund) {
        funds.add(fund);
        fund.setClient(this);
    }

    public void removeFund(Fund fund) {
        funds.remove(fund);
        fund.setClient(null);
    }
}
