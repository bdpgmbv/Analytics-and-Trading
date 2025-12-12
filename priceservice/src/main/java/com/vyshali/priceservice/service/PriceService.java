package com.vyshali.priceservice.service;

import com.vyshali.fxanalyzer.common.dto.PriceDto;
import com.vyshali.fxanalyzer.common.entity.Price;
import com.vyshali.fxanalyzer.common.entity.Product;
import com.vyshali.fxanalyzer.common.event.PriceUpdatedEvent;
import com.vyshali.fxanalyzer.common.exception.EntityNotFoundException;
import com.vyshali.fxanalyzer.common.exception.PriceNotAvailableException;
import com.vyshali.fxanalyzer.common.repository.PriceRepository;
import com.vyshali.fxanalyzer.common.repository.ProductRepository;
import com.vyshali.fxanalyzer.priceservice.cache.PriceCacheService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
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
 * Price Service with hierarchy: OVERRIDE (1) > REALTIME (2) > RCP_SNAP (3) > MSPA (4)
 * 
 * Features:
 * - Two-level caching (L1 Caffeine + L2 Redis)
 * - Circuit breaker for upstream services
 * - WebSocket broadcast for real-time updates
 * - Kafka events for downstream consumers
 * - Staleness detection
 */
@Slf4j
@Service
public class PriceService {

    private final PriceRepository priceRepository;
    private final ProductRepository productRepository;
    private final PriceCacheService cacheService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final SimpMessagingTemplate webSocketTemplate;
    
    // Metrics
    private final Counter priceRequestCounter;
    private final Counter stalePriceCounter;
    private final Timer priceLookupTimer;

    public PriceService(PriceRepository priceRepository,
                        ProductRepository productRepository,
                        PriceCacheService cacheService,
                        KafkaTemplate<String, Object> kafkaTemplate,
                        SimpMessagingTemplate webSocketTemplate,
                        MeterRegistry meterRegistry) {
        this.priceRepository = priceRepository;
        this.productRepository = productRepository;
        this.cacheService = cacheService;
        this.kafkaTemplate = kafkaTemplate;
        this.webSocketTemplate = webSocketTemplate;
        
        this.priceRequestCounter = Counter.builder("price.requests")
                .description("Total price requests")
                .register(meterRegistry);
        
        this.stalePriceCounter = Counter.builder("price.stale")
                .description("Stale prices returned")
                .register(meterRegistry);
        
        this.priceLookupTimer = Timer.builder("price.lookup.time")
                .description("Price lookup duration")
                .register(meterRegistry);
    }

    /**
     * Get best available price for a product using hierarchy.
     * Cache path: L1 -> L2 -> Database
     */
    @CircuitBreaker(name = "priceService", fallbackMethod = "getPriceFallback")
    public PriceDto getPrice(Long productId) {
        priceRequestCounter.increment();
        
        return priceLookupTimer.record(() -> {
            // Try cache first
            Optional<PriceDto> cached = cacheService.getPrice(productId);
            if (cached.isPresent()) {
                return cached.get();
            }
            
            // Load from database using hierarchy
            LocalDate today = LocalDate.now();
            List<Price> prices = priceRepository.findPricesByProductAndDateOrderByPriority(productId, today);
            
            if (prices.isEmpty()) {
                throw new PriceNotAvailableException(String.valueOf(productId), today);
            }
            
            // Get best price (first in priority order)
            Price bestPrice = prices.get(0);
            PriceDto dto = mapToDto(bestPrice);
            
            // Check staleness
            if (Boolean.TRUE.equals(bestPrice.getIsStale())) {
                stalePriceCounter.increment();
                log.warn("Returning stale price for product {}", productId);
            }
            
            // Cache the result
            cacheService.putPrice(productId, dto);
            
            return dto;
        });
    }

    /**
     * Fallback when circuit breaker is open.
     */
    public PriceDto getPriceFallback(Long productId, Exception e) {
        log.warn("Price service fallback triggered for product {}: {}", productId, e.getMessage());
        
        // Try cache even if stale
        Optional<PriceDto> cached = cacheService.getPrice(productId);
        if (cached.isPresent()) {
            PriceDto price = cached.get();
            price.setIsStale(true);
            return price;
        }
        
        throw new PriceNotAvailableException(String.valueOf(productId), LocalDate.now(), e);
    }

    /**
     * Get prices for multiple products.
     */
    public List<PriceDto> getPrices(List<Long> productIds) {
        return productIds.stream()
                .map(this::getPriceSafe)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    private Optional<PriceDto> getPriceSafe(Long productId) {
        try {
            return Optional.of(getPrice(productId));
        } catch (Exception e) {
            log.warn("Failed to get price for product {}: {}", productId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Get price by identifier (CUSIP, ISIN, etc.)
     */
    public PriceDto getPriceByIdentifier(String identifierType, String identifier) {
        Product product = productRepository.findByIdentifierTypeAndIdentifier(identifierType, identifier)
                .orElseThrow(() -> EntityNotFoundException.product(identifier));
        
        return getPrice(product.getProductId());
    }

    /**
     * Update price (from upstream sources).
     */
    @Transactional
    public PriceDto updatePrice(Long productId, BigDecimal priceValue, String source, Integer sourcePriority) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> EntityNotFoundException.product(String.valueOf(productId)));
        
        LocalDate today = LocalDate.now();
        
        // Get existing price to compare
        Optional<Price> existingPrice = priceRepository.findBestPrice(productId, today);
        BigDecimal previousPrice = existingPrice.map(Price::getPriceValue).orElse(null);
        
        // Create or update price
        Price price = Price.builder()
                .product(product)
                .priceDate(today)
                .priceTime(LocalDateTime.now())
                .priceValue(priceValue)
                .source(source)
                .sourcePriority(sourcePriority)
                .isStale(false)
                .build();
        
        price = priceRepository.save(price);
        PriceDto dto = mapToDto(price);
        
        // Update cache
        cacheService.putPrice(productId, dto);
        
        // Publish events
        publishPriceUpdate(product, dto, previousPrice);
        
        log.info("Updated price for {} to {} from {}", product.getTicker(), priceValue, source);
        
        return dto;
    }

    /**
     * Mark prices as stale (called by staleness checker).
     */
    @Transactional
    public int markPricesAsStale(LocalDate cutoffDate) {
        int count = priceRepository.markOldRealtimePricesAsStale(cutoffDate);
        log.info("Marked {} prices as stale", count);
        return count;
    }

    /**
     * Get all stale prices.
     */
    public List<PriceDto> getStalePrices() {
        return priceRepository.findStalePrices(LocalDate.now()).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /**
     * Publish price update via WebSocket and Kafka.
     */
    private void publishPriceUpdate(Product product, PriceDto dto, BigDecimal previousPrice) {
        PriceUpdatedEvent event = PriceUpdatedEvent.builder()
                .productId(product.getProductId())
                .identifier(product.getIdentifier())
                .ticker(product.getTicker())
                .price(dto.getPrice())
                .previousPrice(previousPrice)
                .source(dto.getSource())
                .updatedAt(LocalDateTime.now())
                .isStale(dto.getIsStale())
                .build();
        
        // WebSocket broadcast
        try {
            webSocketTemplate.convertAndSend("/topic/prices/" + product.getProductId(), dto);
            webSocketTemplate.convertAndSend("/topic/prices/all", dto);
        } catch (Exception e) {
            log.warn("Failed to broadcast price via WebSocket: {}", e.getMessage());
        }
        
        // Kafka publish
        try {
            kafkaTemplate.send(PriceUpdatedEvent.TOPIC, product.getTicker(), event);
        } catch (Exception e) {
            log.warn("Failed to publish price to Kafka: {}", e.getMessage());
        }
    }

    private PriceDto mapToDto(Price price) {
        return PriceDto.builder()
                .priceId(price.getPriceId())
                .productId(price.getProduct().getProductId())
                .identifier(price.getProduct().getIdentifier())
                .ticker(price.getProduct().getTicker())
                .securityDescription(price.getProduct().getSecurityDescription())
                .priceDate(price.getPriceDate())
                .priceTime(price.getPriceTime())
                .price(price.getPriceValue())
                .bidPrice(price.getBidPrice())
                .askPrice(price.getAskPrice())
                .source(price.getSource())
                .sourcePriority(price.getSourcePriority())
                .isStale(price.getIsStale())
                .currency(price.getProduct().getIssueCurrency())
                .build();
    }
}
