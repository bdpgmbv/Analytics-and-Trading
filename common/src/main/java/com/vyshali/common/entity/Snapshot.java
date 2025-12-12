package com.vyshali.common.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "snapshots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Snapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "snapshot_id")
    private Long snapshotId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "snapshot_type", nullable = false, length = 20)
    private String snapshotType; // EOD, INTRADAY

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "snapshot_time", nullable = false)
    private LocalDateTime snapshotTime;

    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "position_count")
    @Builder.Default
    private Integer positionCount = 0;

    @Column(name = "total_mv_base", precision = 18, scale = 4)
    private BigDecimal totalMvBase;

    @Column(name = "source_system", length = 20)
    private String sourceSystem;

    @OneToMany(mappedBy = "snapshot", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    private List<Position> positions = new ArrayList<>();

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

    public boolean isEod() {
        return "EOD".equals(snapshotType);
    }

    public boolean isIntraday() {
        return "INTRADAY".equals(snapshotType);
    }

    public void addPosition(Position position) {
        positions.add(position);
        position.setSnapshot(this);
        position.setAccount(this.account);
        this.positionCount = positions.size();
    }

    public void removePosition(Position position) {
        positions.remove(position);
        position.setSnapshot(null);
        this.positionCount = positions.size();
    }

    /**
     * Recalculate total market value from positions
     */
    public void recalculateTotals() {
        this.positionCount = positions.size();
        this.totalMvBase = positions.stream()
            .filter(p -> p.getMarketValueBase() != null)
            .map(Position::getMarketValueBase)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Mark snapshot as superseded (when new snapshot arrives)
     */
    public void supersede() {
        this.status = "SUPERSEDED";
    }
}
