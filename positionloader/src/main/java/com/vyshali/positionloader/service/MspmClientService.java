package com.vyshali.positionloader.service;

import com.vyshali.positionloader.config.LoaderProperties;
import com.vyshali.positionloader.dto.PositionDto;
import com.vyshali.positionloader.exception.MspmClientException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Client service for MSPM API integration.
 */
@Service
public class MspmClientService {
    
    private static final Logger log = LoggerFactory.getLogger(MspmClientService.class);
    
    private final RestClient restClient;
    private final LoaderProperties properties;
    private final Timer apiTimer;
    private final Counter apiCalls;
    private final Counter apiErrors;
    
    public MspmClientService(
            RestClient.Builder restClientBuilder,
            LoaderProperties properties,
            MeterRegistry meterRegistry) {
        this.properties = properties;
        this.restClient = restClientBuilder
            .baseUrl(properties.mspm().baseUrl())
            .build();
        this.apiTimer = Timer.builder("mspm.api.time")
            .description("MSPM API call time")
            .register(meterRegistry);
        this.apiCalls = Counter.builder("mspm.api.calls")
            .description("MSPM API calls")
            .register(meterRegistry);
        this.apiErrors = Counter.builder("mspm.api.errors")
            .description("MSPM API errors")
            .register(meterRegistry);
    }
    
    /**
     * Fetch positions from MSPM for account and date.
     */
    public List<PositionDto> fetchPositions(int accountId, LocalDate businessDate) {
        log.info("Fetching positions from MSPM for account {} date {}", accountId, businessDate);
        apiCalls.increment();
        
        return apiTimer.record(() -> {
            try {
                MspmResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/accounts/{accountId}/positions")
                        .queryParam("businessDate", businessDate.toString())
                        .build(accountId))
                    .retrieve()
                    .body(MspmResponse.class);
                
                if (response == null || response.positions == null) {
                    log.warn("Empty response from MSPM for account {} date {}", 
                        accountId, businessDate);
                    return List.of();
                }
                
                List<PositionDto> positions = response.positions.stream()
                    .map(mp -> mapToPositionDto(mp, accountId, businessDate))
                    .toList();
                
                log.info("Retrieved {} positions from MSPM for account {} date {}", 
                    positions.size(), accountId, businessDate);
                
                return positions;
                
            } catch (RestClientException e) {
                apiErrors.increment();
                log.error("MSPM API error for account {} date {}: {}", 
                    accountId, businessDate, e.getMessage());
                throw new MspmClientException("MSPM API call failed: " + e.getMessage(), e);
            } catch (Exception e) {
                apiErrors.increment();
                log.error("Failed to fetch from MSPM for account {} date {}", 
                    accountId, businessDate, e);
                throw new MspmClientException("MSPM API call failed: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * Check account status in MSPM.
     */
    public AccountStatus getAccountStatus(int accountId) {
        try {
            return restClient.get()
                .uri("/api/v1/accounts/{accountId}/status", accountId)
                .retrieve()
                .body(AccountStatus.class);
        } catch (Exception e) {
            log.warn("Failed to get account status for {}: {}", accountId, e.getMessage());
            return new AccountStatus(accountId, "UNKNOWN", null);
        }
    }
    
    /**
     * Map MSPM position to PositionDto.
     */
    private PositionDto mapToPositionDto(MspmPosition mp, int accountId, LocalDate businessDate) {
        return new PositionDto(
            null,
            accountId,
            mp.productId,
            businessDate,
            mp.quantity != null ? mp.quantity : BigDecimal.ZERO,
            mp.price != null ? mp.price : BigDecimal.ZERO,
            mp.currency != null ? mp.currency : "USD",
            mp.marketValueLocal != null ? mp.marketValueLocal : BigDecimal.ZERO,
            mp.marketValueBase != null ? mp.marketValueBase : BigDecimal.ZERO,
            mp.avgCostPrice != null ? mp.avgCostPrice : BigDecimal.ZERO,
            mp.costLocal != null ? mp.costLocal : BigDecimal.ZERO,
            0,
            "MSPM",
            mp.positionType != null ? mp.positionType : "PHYSICAL",
            false
        );
    }
    
    /**
     * MSPM API response.
     */
    public static class MspmResponse {
        public List<MspmPosition> positions;
        public String status;
        public String message;
    }
    
    /**
     * MSPM position data.
     */
    public static class MspmPosition {
        public int productId;
        public BigDecimal quantity;
        public BigDecimal price;
        public String currency;
        public BigDecimal marketValueLocal;
        public BigDecimal marketValueBase;
        public BigDecimal avgCostPrice;
        public BigDecimal costLocal;
        public String positionType;
    }
    
    /**
     * Account status.
     */
    public record AccountStatus(
        int accountId,
        String status,
        LocalDate lastUpdated
    ) {}
}
