package com.vyshali.positionloader.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * DTO representing a position message from MSPM (MS Position Management).
 * This is the Kafka message format from the upstream position system.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MspmPositionMessage {

    @JsonProperty("message_id")
    private String messageId;
    
    @JsonProperty("message_type")
    private String messageType;  // POSITION_SNAPSHOT, POSITION_UPDATE
    
    @JsonProperty("source_system")
    private String sourceSystem;  // MSPM
    
    @JsonProperty("account_number")
    private String accountNumber;
    
    @JsonProperty("snapshot_type")
    private String snapshotType;  // EOD, INTRADAY
    
    @JsonProperty("snapshot_date")
    private LocalDate snapshotDate;
    
    @JsonProperty("snapshot_time")
    private LocalTime snapshotTime;
    
    @JsonProperty("positions")
    private List<PositionData> positions;
    
    @JsonProperty("timestamp")
    private Long timestamp;

    /**
     * Individual position data within the message
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PositionData {
        
        @JsonProperty("identifier_type")
        private String identifierType;  // CUSIP, ISIN, SEDOL
        
        @JsonProperty("identifier")
        private String identifier;
        
        @JsonProperty("ticker")
        private String ticker;
        
        @JsonProperty("security_description")
        private String securityDescription;
        
        @JsonProperty("asset_class")
        private String assetClass;  // EQUITY, EQUITY_SWAP, FX_FORWARD, etc.
        
        @JsonProperty("issue_currency")
        private String issueCurrency;
        
        @JsonProperty("settlement_currency")
        private String settlementCurrency;
        
        @JsonProperty("quantity")
        private BigDecimal quantity;
        
        @JsonProperty("cost_basis_local")
        private BigDecimal costBasisLocal;
        
        @JsonProperty("cost_basis_base")
        private BigDecimal costBasisBase;
        
        @JsonProperty("market_value_local")
        private BigDecimal marketValueLocal;
        
        @JsonProperty("market_value_base")
        private BigDecimal marketValueBase;
        
        @JsonProperty("price")
        private BigDecimal price;
        
        @JsonProperty("fx_rate")
        private BigDecimal fxRate;
        
        @JsonProperty("position_type")
        private String positionType;
        
        @JsonProperty("exposures")
        private List<ExposureData> exposures;
    }

    /**
     * Currency exposure data for a position
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExposureData {
        
        @JsonProperty("exposure_type")
        private String exposureType;  // GENERIC, SPECIFIC
        
        @JsonProperty("currency")
        private String currency;
        
        @JsonProperty("weight_percent")
        private BigDecimal weightPercent;
    }
}
