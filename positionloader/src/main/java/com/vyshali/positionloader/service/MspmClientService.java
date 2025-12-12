package com.vyshali.positionloader.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyshali.common.config.ResilienceConfig;
import com.vyshali.common.dto.PositionDto;
import com.vyshali.common.service.AlertService;
import com.vyshali.positionloader.config.LoaderConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Client service for interacting with MSPM (Morgan Stanley Portfolio Management).
 * Implements resilience patterns: circuit breaker, retry, rate limiting.
 */
@Slf4j
@Service
public class MspmClientService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final LoaderConfig config;
    private final AlertService alertService;
    private final MeterRegistry meterRegistry;

    // Resilience components
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final RateLimiter rateLimiter;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    public MspmClientService(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            LoaderConfig config,
            AlertService alertService,
            MeterRegistry meterRegistry,
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry,
            RateLimiterRegistry rateLimiterRegistry
    ) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.config = config;
        this.alertService = alertService;
        this.meterRegistry = meterRegistry;

        // Get resilience components using the MSPM_SERVICE constant from common
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(ResilienceConfig.MSPM_SERVICE);
        this.retry = retryRegistry.retry(ResilienceConfig.MSPM_SERVICE);
        this.rateLimiter = rateLimiterRegistry.rateLimiter(ResilienceConfig.MSPM_SERVICE);

        // Register circuit breaker event handlers
        registerCircuitBreakerEvents();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get EOD positions for an account from MSPM.
     * @param accountId Account ID
     * @param businessDate Business date
     * @return List of positions
     */
    public List<PositionDto> getEodPositions(int accountId, LocalDate businessDate) {
        String url = buildUrl("/accounts/{accountId}/positions/eod", accountId, businessDate);

        return executeWithResilience(
                () -> fetchPositions(url, accountId, businessDate),
                "getEodPositions",
                accountId
        );
    }

    /**
     * Get current (intraday) positions for an account.
     * @param accountId Account ID
     * @param businessDate Business date
     * @return List of positions
     */
    public List<PositionDto> getIntradayPositions(int accountId, LocalDate businessDate) {
        String url = buildUrl("/accounts/{accountId}/positions/intraday", accountId, businessDate);

        return executeWithResilience(
                () -> fetchPositions(url, accountId, businessDate),
                "getIntradayPositions",
                accountId
        );
    }

    /**
     * Get positions for reconciliation (used by ReconciliationService).
     * @param accountId Account ID
     * @param businessDate Business date
     * @return List of MSPM positions for reconciliation
     */
    public List<ReconciliationService.MspmPosition> getPositionsForAccount(int accountId, LocalDate businessDate) {
        String url = buildUrl("/accounts/{accountId}/positions", accountId, businessDate);

        return executeWithResilience(
                () -> fetchMspmPositions(url, accountId, businessDate),
                "getPositionsForAccount",
                accountId
        );
    }

    /**
     * Get reference data for an account.
     * @param accountId Account ID
     * @return Account reference data
     */
    public AccountReferenceData getAccountReferenceData(int accountId) {
        String url = config.mspm().baseUrl() + "/accounts/" + accountId + "/reference";

        return executeWithResilience(
                () -> fetchAccountReference(url, accountId),
                "getAccountReferenceData",
                accountId
        );
    }

    /**
     * Health check for MSPM service.
     * @return true if MSPM is reachable
     */
    public boolean healthCheck() {
        try {
            String url = config.mspm().baseUrl() + "/health";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("MSPM health check failed: {}", e.getMessage());
            return false;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // INTERNAL FETCH METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Fetch positions from MSPM.
     */
    private List<PositionDto> fetchPositions(String url, int accountId, LocalDate businessDate) {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            HttpHeaders headers = createHeaders();
            HttpEntity<?> entity = new HttpEntity<>(headers);

            log.debug("Fetching positions from MSPM: url={}", url);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    String.class,
                    accountId,
                    businessDate.format(DATE_FORMAT)
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new MspmClientException("MSPM returned error: " + response.getStatusCode());
            }

            List<PositionDto> positions = objectMapper.readValue(
                    response.getBody(),
                    new TypeReference<List<PositionDto>>() {}
            );

            log.debug("Fetched {} positions for account {}", positions.size(), accountId);
            meterRegistry.counter("mspm.positions.fetched", "account", String.valueOf(accountId))
                    .increment(positions.size());

            return positions;

        } catch (RestClientException e) {
            log.error("REST client error fetching positions: {}", e.getMessage());
            throw new MspmClientException("Failed to fetch positions from MSPM", e);
        } catch (Exception e) {
            log.error("Error processing MSPM response: {}", e.getMessage());
            throw new MspmClientException("Failed to process MSPM response", e);
        } finally {
            sample.stop(Timer.builder("mspm.fetch.positions")
                    .tag("accountId", String.valueOf(accountId))
                    .register(meterRegistry));
        }
    }

    /**
     * Fetch MSPM positions for reconciliation.
     */
    private List<ReconciliationService.MspmPosition> fetchMspmPositions(String url, int accountId, LocalDate businessDate) {
        List<PositionDto> dtos = fetchPositions(url, accountId, businessDate);

        return dtos.stream()
                .map(dto -> new ReconciliationService.MspmPosition(
                        dto.securityId(),
                        dto.quantity(),
                        dto.marketValue(),
                        dto.costBasis(),
                        dto.currency()
                ))
                .toList();
    }

    /**
     * Fetch account reference data.
     */
    private AccountReferenceData fetchAccountReference(String url, int accountId) {
        try {
            HttpHeaders headers = createHeaders();
            HttpEntity<?> entity = new HttpEntity<>(headers);

            ResponseEntity<AccountReferenceData> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    AccountReferenceData.class
            );

            return response.getBody();

        } catch (Exception e) {
            log.error("Failed to fetch account reference data: {}", e.getMessage());
            throw new MspmClientException("Failed to fetch account reference data", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RESILIENCE HANDLING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Execute operation with circuit breaker, retry, and rate limiting.
     */
    private <T> T executeWithResilience(Supplier<T> operation, String operationName, int accountId) {
        // Compose resilience decorators
        Supplier<T> decoratedSupplier = CircuitBreaker.decorateSupplier(circuitBreaker,
                Retry.decorateSupplier(retry,
                        RateLimiter.decorateSupplier(rateLimiter, operation)));

        try {
            return decoratedSupplier.get();
        } catch (Exception e) {
            log.error("MSPM operation {} failed for account {}: {}", operationName, accountId, e.getMessage());
            meterRegistry.counter("mspm.operations.failed",
                            "operation", operationName,
                            "account", String.valueOf(accountId))
                    .increment();

            // Return empty list for position operations, throw for others
            if (operationName.contains("Position")) {
                log.warn("Returning empty list due to MSPM failure");
                return (T) Collections.emptyList();
            }
            throw e;
        }
    }

    /**
     * Register circuit breaker event handlers for alerting.
     */
    private void registerCircuitBreakerEvents() {
        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> {
                    log.warn("MSPM circuit breaker state changed: {} -> {}",
                            event.getStateTransition().getFromState(),
                            event.getStateTransition().getToState());

                    boolean isOpen = event.getStateTransition().getToState() == CircuitBreaker.State.OPEN;
                    alertService.circuitBreakerStateChange("MSPM", isOpen);
                })
                .onError(event -> {
                    log.error("MSPM circuit breaker error: {}", event.getThrowable().getMessage());
                    meterRegistry.counter("mspm.circuit.errors").increment();
                });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Build MSPM URL with path and parameters.
     */
    private String buildUrl(String path, int accountId, LocalDate businessDate) {
        return config.mspm().baseUrl() + path + "?businessDate=" + businessDate.format(DATE_FORMAT);
    }

    /**
     * Create HTTP headers for MSPM requests.
     */
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/json");
        headers.set("Content-Type", "application/json");
        headers.set("X-Client-Id", "fxan-loader");
        headers.set("X-Request-Id", java.util.UUID.randomUUID().toString());
        return headers;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DTOs AND EXCEPTIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Account reference data from MSPM.
     */
    public record AccountReferenceData(
            int accountId,
            String accountName,
            String baseCurrency,
            String accountType,
            boolean active,
            String custodian
    ) {}

    /**
     * Exception for MSPM client errors.
     */
    public static class MspmClientException extends RuntimeException {
        public MspmClientException(String message) {
            super(message);
        }

        public MspmClientException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}