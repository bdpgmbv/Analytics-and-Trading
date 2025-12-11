package com.vyshali.positionloader.service;

import com.fxanalyzer.positionloader.config.LoaderProperties;
import com.fxanalyzer.positionloader.dto.PositionDto;
import com.fxanalyzer.positionloader.exception.MspmClientException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * MSPM (Multi-Source Position Manager) client service.
 * 
 * Handles:
 * - Fetching positions from MSPM API
 * - Circuit breaker and retry logic
 * - Rate limiting
 */
@Service
public class MspmClientService {
    
    private static final Logger log = LoggerFactory.getLogger(MspmClientService.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_DATE;
    
    private final RestClient mspmRestClient;
    private final LoaderProperties loaderProperties;
    private final MeterRegistry meterRegistry;
    
    public MspmClientService(
            RestClient mspmRestClient,
            LoaderProperties loaderProperties,
            MeterRegistry meterRegistry) {
        this.mspmRestClient = mspmRestClient;
        this.loaderProperties = loaderProperties;
        this.meterRegistry = meterRegistry;
    }
    
    /**
     * Fetch positions for an account from MSPM.
     * 
     * Uses circuit breaker, retry, and rate limiting.
     */
    @CircuitBreaker(name = "mspm", fallbackMethod = "fetchPositionsFallback")
    @Retry(name = "mspm")
    @RateLimiter(name = "mspm")
    public List<PositionDto> fetchPositions(int accountId, LocalDate businessDate) {
        Timer.Sample timer = Timer.start(meterRegistry);
        
        try {
            log.debug("Fetching positions from MSPM: account={} date={}", 
                accountId, businessDate);
            
            String dateStr = businessDate.format(DATE_FORMAT);
            
            List<PositionDto> positions = mspmRestClient
                .get()
                .uri("/api/v1/accounts/{accountId}/positions?date={date}", 
                    accountId, dateStr)
                .retrieve()
                .body(new ParameterizedTypeReference<List<PositionDto>>() {});
            
            log.info("Fetched {} positions from MSPM for account {}", 
                positions != null ? positions.size() : 0, accountId);
            
            return positions != null ? positions : List.of();
            
        } catch (Exception e) {
            log.error("MSPM API call failed: account={} date={}", 
                accountId, businessDate, e);
            throw new MspmClientException("Failed to fetch positions from MSPM", e);
            
        } finally {
            timer.stop(meterRegistry.timer("mspm.fetch.duration", 
                "account", String.valueOf(accountId)));
        }
    }
    
    /**
     * Fallback when MSPM is unavailable.
     */
    public List<PositionDto> fetchPositionsFallback(int accountId, LocalDate businessDate, 
            Throwable t) {
        log.error("MSPM fallback triggered for account {} on {}: {}", 
            accountId, businessDate, t.getMessage());
        throw new MspmClientException("MSPM service unavailable", t);
    }
    
    /**
     * Check if MSPM service is healthy.
     */
    public boolean isHealthy() {
        try {
            mspmRestClient.get()
                .uri("/actuator/health")
                .retrieve()
                .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.warn("MSPM health check failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Fetch positions with pagination for large accounts.
     */
    @CircuitBreaker(name = "mspm")
    @Retry(name = "mspm")
    public List<PositionDto> fetchPositionsPaginated(int accountId, LocalDate businessDate, 
            int page, int size) {
        
        String dateStr = businessDate.format(DATE_FORMAT);
        
        return mspmRestClient
            .get()
            .uri("/api/v1/accounts/{accountId}/positions?date={date}&page={page}&size={size}", 
                accountId, dateStr, page, size)
            .retrieve()
            .body(new ParameterizedTypeReference<List<PositionDto>>() {});
    }
}
