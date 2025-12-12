package com.vyshali.hedgeservice.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "Snapshots")
public class Snapshot {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "snapshot_id")
    private Long snapshotId;
    
    @Column(name = "portfolio_id", nullable = false)
    private Integer portfolioId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", insertable = false, updatable = false)
    private Portfolio portfolio;
    
    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;
    
    @Column(name = "snapshot_type", length = 20)
    private String snapshotType;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
