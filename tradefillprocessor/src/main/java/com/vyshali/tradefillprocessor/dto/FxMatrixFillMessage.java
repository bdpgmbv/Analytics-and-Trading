package com.vyshali.tradefillprocessor.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO representing a trade fill message from FXMatrix.
 * This is the Kafka message format from the FX execution platform.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FxMatrixFillMessage {

    /**
     * Unique execution reference from FXMatrix
     */
    @JsonProperty("execution_ref")
    private String executionRef;

    /**
     * Our internal trade reference (sent with the order)
     */
    @JsonProperty("client_order_ref")
    private String clientOrderRef;

    /**
     * Message type: FILL, REJECT, CANCEL, ACK
     */
    @JsonProperty("message_type")
    private String messageType;

    /**
     * Execution status: EXECUTED, REJECTED, CANCELLED, PARTIAL_FILL
     */
    @JsonProperty("status")
    private String status;

    /**
     * Trade type: SPOT, FORWARD, SWAP
     */
    @JsonProperty("trade_type")
    private String tradeType;

    /**
     * Buy currency (e.g., EUR)
     */
    @JsonProperty("buy_currency")
    private String buyCurrency;

    /**
     * Sell currency (e.g., USD)
     */
    @JsonProperty("sell_currency")
    private String sellCurrency;

    /**
     * Buy amount
     */
    @JsonProperty("buy_amount")
    private BigDecimal buyAmount;

    /**
     * Sell amount
     */
    @JsonProperty("sell_amount")
    private BigDecimal sellAmount;

    /**
     * Execution rate
     */
    @JsonProperty("execution_rate")
    private BigDecimal executionRate;

    /**
     * Spot rate at time of execution
     */
    @JsonProperty("spot_rate")
    private BigDecimal spotRate;

    /**
     * Forward points (for forward trades)
     */
    @JsonProperty("forward_points")
    private BigDecimal forwardPoints;

    /**
     * Value date (settlement date)
     */
    @JsonProperty("value_date")
    private LocalDate valueDate;

    /**
     * Execution timestamp
     */
    @JsonProperty("execution_time")
    private LocalDateTime executionTime;

    /**
     * Counterparty code
     */
    @JsonProperty("counterparty")
    private String counterparty;

    /**
     * Account number
     */
    @JsonProperty("account_number")
    private String accountNumber;

    /**
     * Reject reason (if rejected)
     */
    @JsonProperty("reject_reason")
    private String rejectReason;

    /**
     * Reject code (if rejected)
     */
    @JsonProperty("reject_code")
    private String rejectCode;

    /**
     * Original message timestamp
     */
    @JsonProperty("timestamp")
    private Long timestamp;

    /**
     * Check if this is a successful execution
     */
    public boolean isExecuted() {
        return "EXECUTED".equalsIgnoreCase(status) || "FILL".equalsIgnoreCase(messageType);
    }

    /**
     * Check if this is a rejection
     */
    public boolean isRejected() {
        return "REJECTED".equalsIgnoreCase(status) || "REJECT".equalsIgnoreCase(messageType);
    }

    /**
     * Check if this is a cancellation
     */
    public boolean isCancelled() {
        return "CANCELLED".equalsIgnoreCase(status) || "CANCEL".equalsIgnoreCase(messageType);
    }

    /**
     * Check if this is an acknowledgment
     */
    public boolean isAcknowledgment() {
        return "ACK".equalsIgnoreCase(messageType);
    }

    /**
     * Get currency pair in standard format
     */
    public String getCurrencyPair() {
        if (buyCurrency != null && sellCurrency != null) {
            return buyCurrency + sellCurrency;
        }
        return null;
    }
}
