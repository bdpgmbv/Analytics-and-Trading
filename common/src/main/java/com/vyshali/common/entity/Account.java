package com.vyshali.common.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_id")
    private Long accountId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fund_id", nullable = false)
    private Fund fund;

    @Column(name = "account_number", nullable = false, unique = true, length = 50)
    private String accountNumber;

    @Column(name = "account_type", nullable = false, length = 20)
    private String accountType;

    @Column(name = "source_system", length = 20)
    @Builder.Default
    private String sourceSystem = "MSPM";

    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Snapshot> snapshots = new ArrayList<>();

    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<CashBalance> cashBalances = new ArrayList<>();

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

    public boolean isMspmSource() {
        return "MSPM".equals(sourceSystem);
    }

    public boolean isManualSource() {
        return "MANUAL".equals(sourceSystem) || "FTP".equals(sourceSystem);
    }

    /**
     * Get the fund's base currency (convenience method)
     */
    public String getBaseCurrency() {
        return fund != null ? fund.getBaseCurrency() : null;
    }

    /**
     * Get the client code (convenience method for reporting)
     */
    public String getClientCode() {
        return fund != null && fund.getClient() != null 
            ? fund.getClient().getClientCode() 
            : null;
    }
}
