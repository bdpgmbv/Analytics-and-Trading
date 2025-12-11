package com.vyshali.positionloader.service;

import com.fxanalyzer.positionloader.dto.PositionDto;
import com.fxanalyzer.positionloader.dto.TradeFillDto;
import com.fxanalyzer.positionloader.repository.PositionRepository;
import com.fxanalyzer.positionloader.repository.ReferenceDataRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Intraday position processing service.
 * 
 * Handles:
 * - Real-time position updates from Kafka
 * - Trade fill processing
 * - Position adjustments
 */
@Service
public class IntradayProcessingService {
    
    private static final Logger log = LoggerFactory.getLogger(IntradayProcessingService.class);
    
    private final PositionRepository positionRepository;
    private final ReferenceDataRepository referenceDataRepository;
    private final PositionValidationService validationService;
    private final Counter updateCounter;
    private final Counter fillCounter;
    
    public IntradayProcessingService(
            PositionRepository positionRepository,
            ReferenceDataRepository referenceDataRepository,
            PositionValidationService validationService,
            MeterRegistry meterRegistry) {
        this.positionRepository = positionRepository;
        this.referenceDataRepository = referenceDataRepository;
        this.validationService = validationService;
        this.updateCounter = meterRegistry.counter("positions.intraday.updates");
        this.fillCounter = meterRegistry.counter("positions.intraday.fills");
    }
    
    /**
     * Process a real-time position update.
     */
    @Transactional
    public void processUpdate(PositionDto position) {
        log.debug("Processing intraday update for account {} product {}", 
            position.accountId(), position.productId());
        
        // Validate
        var errors = validationService.validateSingle(position);
        if (!errors.isEmpty()) {
            log.warn("Position validation failed: {}", errors);
            throw new IllegalArgumentException("Invalid position: " + errors);
        }
        
        // Upsert position
        positionRepository.upsertPosition(position);
        updateCounter.increment();
        
        log.debug("Intraday update processed for account {} product {}", 
            position.accountId(), position.productId());
    }
    
    /**
     * Process a trade fill and update positions.
     */
    @Transactional
    public void processTradeFill(String orderId, String symbol, String side, 
            double quantity, double price) {
        
        log.info("Processing trade fill: order={} symbol={} side={} qty={} price={}", 
            orderId, symbol, side, quantity, price);
        
        // Lookup product
        Integer productId = referenceDataRepository.findProductIdByTicker(symbol);
        if (productId == null) {
            log.error("Unknown product ticker: {}", symbol);
            throw new IllegalArgumentException("Unknown product: " + symbol);
        }
        
        // Lookup account from order
        Integer accountId = referenceDataRepository.findAccountIdByOrderId(orderId);
        if (accountId == null) {
            log.error("Cannot find account for order: {}", orderId);
            throw new IllegalArgumentException("Unknown order: " + orderId);
        }
        
        // Calculate signed quantity (negative for sells)
        BigDecimal signedQty = BigDecimal.valueOf(quantity);
        if ("SELL".equalsIgnoreCase(side)) {
            signedQty = signedQty.negate();
        }
        
        // Update position
        LocalDate today = LocalDate.now();
        positionRepository.updateQuantity(accountId, productId, today, signedQty, 
            BigDecimal.valueOf(price));
        
        fillCounter.increment();
        
        log.info("Trade fill processed: account={} product={} qty={}", 
            accountId, productId, signedQty);
    }
    
    /**
     * Process a batch of trade fills.
     */
    @Transactional
    public void processTradeFills(java.util.List<TradeFillDto> fills) {
        log.info("Processing {} trade fills", fills.size());
        
        for (TradeFillDto fill : fills) {
            try {
                processTradeFill(fill.orderId(), fill.symbol(), fill.side(), 
                    fill.quantity(), fill.price());
            } catch (Exception e) {
                log.error("Failed to process fill: {}", fill, e);
                // Continue processing other fills
            }
        }
    }
}
