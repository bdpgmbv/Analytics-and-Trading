package com.vyshali.positionloader.service;

import com.vyshali.positionloader.config.LoaderProperties;
import com.vyshali.positionloader.config.ResilienceConfig;
import com.vyshali.positionloader.dto.PositionDto;
import com.vyshali.positionloader.exception.MspmClientException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
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
import java.util.function.Supplier;

/**
 * Client service for MSPM API integration.
 * 
 * Features:
 * - Circuit breaker to prevent cascade failures
 * - Retry with exponential backoff
 * - Comprehensive metrics
 */
@Service
public class MspmClientService {
    
    private static final Logger log = LoggerFactory.getLogger(MspmClientService.class);
    
    private final RestClient restClient;
    private final LoaderProperties properties;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final Timer apiTimer;
    private final Counter apiCalls;
    private final Counter apiErrors;
    private final Counter circuitBreakerOpen;
    
    public MspmClientService(
            RestClient.Builder restClientBuilder,
            LoaderProperties properties,
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry,
            MeterRegistry meterRegistry) {
        this.properties = properties;
        this.restClient = restClientBuilder
            .baseUrl(properties.mspm().baseUrl())
            .build();
        
        // Get or create circuit breaker and retry
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(ResilienceConfig.MSPM_SERVICE);
        this.retry = retryRegistry.retry(ResilienceConfig.MSPM_SERVICE);
        
        // Setup metrics
        this.apiTimer = Timer.builder("mspm.api.time")
            .description("MSPM API call time")
            .register(meterRegistry);
        this.apiCalls = Counter.builder("mspm.api.calls")
            .description("MSPM API calls")
            .register(meterRegistry);
        this.apiErrors = Counter.builder("mspm.api.errors")
            .description("MSPM API errors")
            .register(meterRegistry);
        this.circuitBreakerOpen = Counter.builder("mspm.circuitbreaker.rejected")
            .description("Requests rejected by circuit breaker")
            .register(meterRegistry);
        
        // Register circuit breaker event listeners
        circuitBreaker.getEventPublisher()
            .onStateTransition(event -> log.warn("MSPM Circuit Breaker state: {} -> {}", 
                event.getStateTransition().getFromState(), 
                event.getStateTransition().getToState()))
            .onCallNotPermitted(event -> {
                circuitBreakerOpen.increment();
                log.warn("MSPM call rejected - circuit breaker OPEN");
            });
    }
    
    /**
     * Fetch positions from MSPM for account and date.
     * Protected by circuit breaker and retry.
     */
    public List<PositionDto> fetchPositions(int accountId, LocalDate businessDate) {
        log.info("Fetching positions from MSPM for account {} date {}", accountId, businessDate);
        apiCalls.increment();
        
        // Wrap the call with retry and circuit breaker
        Supplier<List<PositionDto>> decoratedSupplier = CircuitBreaker.decorateSupplier(
            circuitBreaker,
            Retry.decorateSupplier(retry, () -> doFetchPositions(accountId, businessDate))
        );
        
        try {
            return decoratedSupplier.get();
        } catch (Exception e) {
            apiErrors.increment();
            if (e.getCause() instanceof MspmClientException) {
                throw (MspmClientException) e.getCause();
            }
            throw new MspmClientException("MSPM call failed after retries: " + e.getMessage(), e);
        }
    }
    
    /**
     * Internal method to actually fetch positions.
     */
    private List<PositionDto> doFetchPositions(int accountId, LocalDate businessDate) {
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
                log.error("MSPM API error for account {} date {}: {}", 
                    accountId, businessDate, e.getMessage());
                throw new MspmClientException("MSPM API call failed: " + e.getMessage(), e);
            } catch (Exception e) {
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
            Supplier<AccountStatus> decoratedSupplier = CircuitBreaker.decorateSupplier(
                circuitBreaker,
                () -> restClient.get()
                    .uri("/api/v1/accounts/{accountId}/status", accountId)
                    .retrieve()
                    .body(AccountStatus.class)
            );
            return decoratedSupplier.get();
        } catch (Exception e) {
            log.warn("Failed to get account status for {}: {}", accountId, e.getMessage());
            return new AccountStatus(accountId, "UNKNOWN", null);
        }
    }
    
    /**
     * Check if MSPM service is available (circuit breaker not open).
     */
    public boolean isAvailable() {
        return !circuitBreaker.getState().equals(CircuitBreaker.State.OPEN);
    }
    
    /**
     * Get circuit breaker state for monitoring.
     */
    public String getCircuitBreakerState() {
        return circuitBreaker.getState().name();
    }
    
    /**
     * Get circuit breaker metrics for monitoring.
     */
    public CircuitBreakerMetrics getCircuitBreakerMetrics() {
        CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();
        return new CircuitBreakerMetrics(
            circuitBreaker.getState().name(),
            metrics.getFailureRate(),
            metrics.getSlowCallRate(),
            metrics.getNumberOfSuccessfulCalls(),
            metrics.getNumberOfFailedCalls(),
            metrics.getNumberOfSlowCalls(),
            metrics.getNumberOfNotPermittedCalls()
        );
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
    
    /**
     * Circuit breaker metrics for monitoring.
     */
    public record CircuitBreakerMetrics(
        String state,
        float failureRate,
        float slowCallRate,
        int successfulCalls,
        int failedCalls,
        int slowCalls,
        long notPermittedCalls
    ) {}
}
