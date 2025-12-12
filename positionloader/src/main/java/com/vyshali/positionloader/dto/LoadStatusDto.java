package com.vyshali.positionloader.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO for position load status response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoadStatusDto {

    private Long snapshotId;
    private String accountNumber;
    private String snapshotType;
    private LocalDate snapshotDate;
    private String status;
    private int positionCount;
    private LocalDateTime loadedAt;
    private String sourceSystem;
    
    /**
     * Status constants
     */
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_LOADING = "LOADING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
}
