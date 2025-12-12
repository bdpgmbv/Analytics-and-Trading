package com.vyshali.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Event published when positions are loaded from MSPM
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PositionLoadedEvent {

    private Long snapshotId;
    private String accountNumber;
    private String snapshotType;  // EOD or INTRADAY
    private LocalDate snapshotDate;
    private LocalDateTime loadedAt;
    private int positionCount;
    private String sourceSystem;
    
    /**
     * Kafka topic for this event
     */
    public static final String TOPIC = "fxanalyzer.positions.loaded";
}
