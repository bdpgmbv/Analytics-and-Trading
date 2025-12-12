package com.vyshali.common.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "funds")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Fund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fund_id")
    private Long fundId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "fund_code", nullable = false, unique = true, length = 20)
    private String fundCode;

    @Column(name = "fund_name", nullable = false, length = 100)
    private String fundName;

    @Column(name = "base_currency", nullable = false, length = 3)
    private String baseCurrency;

    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    @OneToMany(mappedBy = "fund", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Account> accounts = new ArrayList<>();

    @OneToMany(mappedBy = "fund", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ShareClass> shareClasses = new ArrayList<>();

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

    public void addAccount(Account account) {
        accounts.add(account);
        account.setFund(this);
    }

    public void removeAccount(Account account) {
        accounts.remove(account);
        account.setFund(null);
    }

    public void addShareClass(ShareClass shareClass) {
        shareClasses.add(shareClass);
        shareClass.setFund(this);
    }
}
