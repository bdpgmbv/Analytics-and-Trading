package com.vyshali.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Event received from FX Matrix when a trade is filled.
 * This is the Kafka message format from upstream FX Matrix system.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeFillEvent {

    private String executionRef;
    private String accountNumber;
    private String counterpartyCode;
    
    // Trade details
    private String tradeType;      // SPOT, FORWARD, SWAP
    private String buyCurrency;
    private String sellCurrency;
    private BigDecimal buyAmount;
    private BigDecimal sellAmount;
    private BigDecimal executionRate;
    
    // Dates
    private LocalDate tradeDate;
    private LocalDate valueDate;
    private LocalDateTime executionTime;
    
    // Status
    private String status;         // EXECUTED, REJECTED, FAILED
    private String rejectReason;
    
    /**
     * Kafka topic for incoming fills from FX Matrix
     */
    public static final String TOPIC_INCOMING = "fxmatrix.trades.fills";
    
    /**
     * Kafka topic for processed fills (downstream to custody, fx cash, TM)
     */
    public static final String TOPIC_PROCESSED = "fxanalyzer.trades.processed";
    
    /**
     * Check if trade was successful
     */
    public boolean isSuccessful() {
        return "EXECUTED".equalsIgnoreCase(status);
    }
    
    /**
     * Check if trade was rejected
     */
    public boolean isRejected() {
        return "REJECTED".equalsIgnoreCase(status);
    }
    
    /**
     * Get currency pair
     */
    public String getCurrencyPair() {
        return buyCurrency + "/" + sellCurrency;
    }
}
