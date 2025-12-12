package com.vyshali.common.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "counterparties")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Counterparty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "counterparty_id")
    private Long counterpartyId;

    @Column(name = "counterparty_code", nullable = false, unique = true, length = 20)
    private String counterpartyCode;

    @Column(name = "counterparty_name", nullable = false, length = 100)
    private String counterpartyName;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Business methods
    public boolean isActive() {
        return Boolean.TRUE.equals(isActive);
    }

    /**
     * Get display name for UI
     */
    public String getDisplayName() {
        return counterpartyCode + " - " + counterpartyName;
    }
}
