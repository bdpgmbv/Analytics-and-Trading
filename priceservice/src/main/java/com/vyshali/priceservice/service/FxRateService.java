package com.vyshali.priceservice.service;

import com.vyshali.fxanalyzer.common.dto.FxRateDto;
import com.vyshali.fxanalyzer.common.entity.FxRate;
import com.vyshali.fxanalyzer.common.exception.FxRateNotAvailableException;
import com.vyshali.fxanalyzer.common.repository.FxRateRepository;
import com.vyshali.fxanalyzer.common.util.CurrencyUtil;
import com.vyshali.fxanalyzer.priceservice.cache.PriceCacheService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * FX Rate Service for currency conversion rates.
 * 
 * Features:
 * - L1/L2 caching
 * - Automatic inverse rate calculation
 * - Forward rate calculation
 * - WebSocket broadcast
 */
@Slf4j
@Service
public class FxRateService {

    private final FxRateRepository fxRateRepository;
    private final PriceCacheService cacheService;
    private final SimpMessagingTemplate webSocketTemplate;
    
    private final Counter fxRateRequestCounter;

    public FxRateService(FxRateRepository fxRateRepository,
                         PriceCacheService cacheService,
                         SimpMessagingTemplate webSocketTemplate,
                         MeterRegistry meterRegistry) {
        this.fxRateRepository = fxRateRepository;
        this.cacheService = cacheService;
        this.webSocketTemplate = webSocketTemplate;
        
        this.fxRateRequestCounter = Counter.builder("fxrate.requests")
                .description("Total FX rate requests")
                .register(meterRegistry);
    }

    /**
     * Get FX rate for a currency pair.
     */
    @CircuitBreaker(name = "fxRateService", fallbackMethod = "getFxRateFallback")
    public FxRateDto getFxRate(String currencyPair) {
        fxRateRequestCounter.increment();
        
        // Normalize pair format (EURUSD or EUR/USD -> EURUSD)
        String normalizedPair = currencyPair.replace("/", "").toUpperCase();
        
        // Try cache first
        Optional<FxRateDto> cached = cacheService.getFxRate(normalizedPair);
        if (cached.isPresent()) {
            return cached.get();
        }
        
        // Load from database
        LocalDate today = LocalDate.now();
        Optional<FxRate> rate = fxRateRepository.findLatestRate(normalizedPair);
        
        if (rate.isPresent()) {
            FxRateDto dto = mapToDto(rate.get());
            cacheService.putFxRate(normalizedPair, dto);
            return dto;
        }
        
        // Try inverse pair
        String inversePair = normalizedPair.substring(3, 6) + normalizedPair.substring(0, 3);
        Optional<FxRate> inverseRate = fxRateRepository.findLatestRate(inversePair);
        
        if (inverseRate.isPresent()) {
            FxRateDto dto = mapToInverseDto(inverseRate.get(), normalizedPair);
            cacheService.putFxRate(normalizedPair, dto);
            return dto;
        }
        
        throw new FxRateNotAvailableException(normalizedPair, today);
    }

    /**
     * Fallback for circuit breaker.
     */
    public FxRateDto getFxRateFallback(String currencyPair, Exception e) {
        log.warn("FX rate service fallback for {}: {}", currencyPair, e.getMessage());
        
        Optional<FxRateDto> cached = cacheService.getFxRate(currencyPair);
        if (cached.isPresent()) {
            FxRateDto rate = cached.get();
            rate.setIsStale(true);
            return rate;
        }
        
        throw new FxRateNotAvailableException(currencyPair, LocalDate.now(), e);
    }

    /**
     * Get conversion rate between two currencies.
     */
    public BigDecimal getConversionRate(String fromCurrency, String toCurrency) {
        if (fromCurrency.equalsIgnoreCase(toCurrency)) {
            return BigDecimal.ONE;
        }
        
        String pair = fromCurrency.toUpperCase() + toCurrency.toUpperCase();
        FxRateDto rate = getFxRate(pair);
        return rate.getMidRate();
    }

    /**
     * Get all available FX rates for today.
     */
    public List<FxRateDto> getAllRates() {
        return fxRateRepository.findByRateDate(LocalDate.now()).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /**
     * Get rates for specific currency pairs.
     */
    public List<FxRateDto> getRates(List<String> currencyPairs) {
        return currencyPairs.stream()
                .map(this::getFxRateSafe)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    private Optional<FxRateDto> getFxRateSafe(String pair) {
        try {
            return Optional.of(getFxRate(pair));
        } catch (Exception e) {
            log.warn("Failed to get FX rate for {}: {}", pair, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Update FX rate.
     */
    @Transactional
    public FxRateDto updateFxRate(String currencyPair, BigDecimal midRate, String source) {
        String[] currencies = CurrencyUtil.parseCurrencyPair(currencyPair);
        String baseCurrency = currencies[0];
        String quoteCurrency = currencies[1];
        
        FxRate rate = FxRate.builder()
                .currencyPair(currencyPair)
                .baseCurrency(baseCurrency)
                .quoteCurrency(quoteCurrency)
                .rateDate(LocalDate.now())
                .rateTime(LocalDateTime.now())
                .midRate(midRate)
                .source(source)
                .isStale(false)
                .build();
        
        rate = fxRateRepository.save(rate);
        FxRateDto dto = mapToDto(rate);
        
        // Update cache
        cacheService.putFxRate(currencyPair, dto);
        
        // Broadcast via WebSocket
        broadcastFxRateUpdate(dto);
        
        log.info("Updated FX rate {} = {}", currencyPair, midRate);
        
        return dto;
    }

    private void broadcastFxRateUpdate(FxRateDto dto) {
        try {
            webSocketTemplate.convertAndSend("/topic/fx-rates/" + dto.getCurrencyPair(), dto);
            webSocketTemplate.convertAndSend("/topic/fx-rates/all", dto);
        } catch (Exception e) {
            log.warn("Failed to broadcast FX rate via WebSocket: {}", e.getMessage());
        }
    }

    private FxRateDto mapToDto(FxRate rate) {
        return FxRateDto.builder()
                .fxRateId(rate.getFxRateId())
                .currencyPair(rate.getCurrencyPair())
                .baseCurrency(rate.getBaseCurrency())
                .quoteCurrency(rate.getQuoteCurrency())
                .rateDate(rate.getRateDate())
                .rateTime(rate.getRateTime())
                .midRate(rate.getMidRate())
                .bidRate(rate.getBidRate())
                .askRate(rate.getAskRate())
                .forwardPoints1M(rate.getForwardPoints1M())
                .forwardPoints3M(rate.getForwardPoints3M())
                .source(rate.getSource())
                .isStale(rate.getIsStale())
                .build();
    }

    private FxRateDto mapToInverseDto(FxRate rate, String requestedPair) {
        BigDecimal inverseMid = CurrencyUtil.invertRate(rate.getMidRate());
        BigDecimal inverseBid = rate.getAskRate() != null ? CurrencyUtil.invertRate(rate.getAskRate()) : null;
        BigDecimal inverseAsk = rate.getBidRate() != null ? CurrencyUtil.invertRate(rate.getBidRate()) : null;
        
        return FxRateDto.builder()
                .fxRateId(rate.getFxRateId())
                .currencyPair(requestedPair)
                .baseCurrency(requestedPair.substring(0, 3))
                .quoteCurrency(requestedPair.substring(3, 6))
                .rateDate(rate.getRateDate())
                .rateTime(rate.getRateTime())
                .midRate(inverseMid)
                .bidRate(inverseBid)
                .askRate(inverseAsk)
                .source(rate.getSource() + " (inverted)")
                .isStale(rate.getIsStale())
                .build();
    }
}
