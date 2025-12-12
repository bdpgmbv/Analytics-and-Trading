package com.vyshali.tradefillprocessor.service;

import com.vyshali.fxanalyzer.common.entity.Account;
import com.vyshali.fxanalyzer.common.entity.Counterparty;
import com.vyshali.fxanalyzer.common.entity.ForwardContract;
import com.vyshali.fxanalyzer.common.entity.TradeExecution;
import com.vyshali.fxanalyzer.common.event.TradeFillEvent;
import com.vyshali.fxanalyzer.common.repository.AccountRepository;
import com.vyshali.fxanalyzer.common.repository.CounterpartyRepository;
import com.vyshali.fxanalyzer.common.repository.ForwardContractRepository;
import com.vyshali.fxanalyzer.common.repository.TradeExecutionRepository;
import com.vyshali.fxanalyzer.tradefillprocessor.dto.FxMatrixFillMessage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Service for processing trade executions from FXMatrix.
 * Maintains full audit trail: SENT → ACKNOWLEDGED → EXECUTED/REJECTED/FAILED
 * 
 * This addresses Issue #2: Trade status audit trail
 */
@Slf4j
@Service
public class TradeExecutionService {

    private final TradeExecutionRepository tradeExecutionRepository;
    private final ForwardContractRepository forwardContractRepository;
    private final AccountRepository accountRepository;
    private final CounterpartyRepository counterpartyRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    // Metrics
    private final Counter fillsProcessedCounter;
    private final Counter fillsExecutedCounter;
    private final Counter fillsRejectedCounter;
    private final Counter fillsFailedCounter;
    private final Timer processingTimer;

    public TradeExecutionService(TradeExecutionRepository tradeExecutionRepository,
                                  ForwardContractRepository forwardContractRepository,
                                  AccountRepository accountRepository,
                                  CounterpartyRepository counterpartyRepository,
                                  KafkaTemplate<String, Object> kafkaTemplate,
                                  MeterRegistry meterRegistry) {
        this.tradeExecutionRepository = tradeExecutionRepository;
        this.forwardContractRepository = forwardContractRepository;
        this.accountRepository = accountRepository;
        this.counterpartyRepository = counterpartyRepository;
        this.kafkaTemplate = kafkaTemplate;
        
        // Initialize metrics
        this.fillsProcessedCounter = Counter.builder("trade.fills.processed")
                .description("Total trade fills processed")
                .register(meterRegistry);
        
        this.fillsExecutedCounter = Counter.builder("trade.fills.executed")
                .description("Successfully executed trades")
                .register(meterRegistry);
        
        this.fillsRejectedCounter = Counter.builder("trade.fills.rejected")
                .description("Rejected trades")
                .register(meterRegistry);
        
        this.fillsFailedCounter = Counter.builder("trade.fills.failed")
                .description("Failed trade processing")
                .register(meterRegistry);
        
        this.processingTimer = Timer.builder("trade.fills.processing.time")
                .description("Trade fill processing duration")
                .register(meterRegistry);
    }

    /**
     * Process a trade fill message from FXMatrix.
     */
    @Transactional
    public void processFill(FxMatrixFillMessage fill) {
        long startTime = System.nanoTime();
        
        try {
            log.info("Processing fill: {} - {} - {}", 
                    fill.getExecutionRef(), fill.getMessageType(), fill.getStatus());
            
            fillsProcessedCounter.increment();
            
            if (fill.isAcknowledgment()) {
                processAcknowledgment(fill);
            } else if (fill.isExecuted()) {
                processExecution(fill);
            } else if (fill.isRejected()) {
                processRejection(fill);
            } else if (fill.isCancelled()) {
                processCancellation(fill);
            } else {
                log.warn("Unknown fill type: {} - {}", fill.getMessageType(), fill.getStatus());
            }
            
        } catch (Exception e) {
            fillsFailedCounter.increment();
            log.error("Failed to process fill {}: {}", fill.getExecutionRef(), e.getMessage(), e);
            throw e;
        } finally {
            long duration = System.nanoTime() - startTime;
            processingTimer.record(duration, TimeUnit.NANOSECONDS);
        }
    }

    /**
     * Process trade acknowledgment (order received by FXMatrix).
     */
    private void processAcknowledgment(FxMatrixFillMessage fill) {
        Optional<TradeExecution> existing = tradeExecutionRepository
                .findByClientOrderRef(fill.getClientOrderRef());
        
        if (existing.isPresent()) {
            TradeExecution execution = existing.get();
            execution.setStatus("ACKNOWLEDGED");
            execution.setFxMatrixRef(fill.getExecutionRef());
            execution.setAcknowledgedAt(LocalDateTime.now());
            tradeExecutionRepository.save(execution);
            
            log.info("Trade {} acknowledged by FXMatrix as {}", 
                    fill.getClientOrderRef(), fill.getExecutionRef());
        } else {
            log.warn("No pending trade found for acknowledgment: {}", fill.getClientOrderRef());
        }
    }

    /**
     * Process successful trade execution.
     */
    private void processExecution(FxMatrixFillMessage fill) {
        fillsExecutedCounter.increment();
        
        // Find or create trade execution record
        TradeExecution execution = findOrCreateExecution(fill);
        
        // Update execution details
        execution.setStatus("EXECUTED");
        execution.setFxMatrixRef(fill.getExecutionRef());
        execution.setExecutionRate(fill.getExecutionRate());
        execution.setSpotRate(fill.getSpotRate());
        execution.setForwardPoints(fill.getForwardPoints());
        execution.setExecutedAt(fill.getExecutionTime() != null ? fill.getExecutionTime() : LocalDateTime.now());
        execution.setValueDate(fill.getValueDate());
        
        tradeExecutionRepository.save(execution);
        
        // If this is a forward trade, create forward contract record
        if ("FORWARD".equalsIgnoreCase(fill.getTradeType())) {
            createForwardContract(fill, execution);
        }
        
        // Publish processed event
        publishProcessedEvent(fill, execution);
        
        log.info("Trade executed: {} - {} {} @ {}", 
                fill.getExecutionRef(), 
                fill.getBuyAmount(), fill.getBuyCurrency(),
                fill.getExecutionRate());
    }

    /**
     * Process trade rejection.
     */
    private void processRejection(FxMatrixFillMessage fill) {
        fillsRejectedCounter.increment();
        
        TradeExecution execution = findOrCreateExecution(fill);
        
        execution.setStatus("REJECTED");
        execution.setFxMatrixRef(fill.getExecutionRef());
        execution.setRejectReason(fill.getRejectReason());
        execution.setRejectCode(fill.getRejectCode());
        execution.setRejectedAt(LocalDateTime.now());
        
        tradeExecutionRepository.save(execution);
        
        // Publish processed event
        publishProcessedEvent(fill, execution);
        
        log.warn("Trade rejected: {} - {} ({})", 
                fill.getExecutionRef(), fill.getRejectReason(), fill.getRejectCode());
    }

    /**
     * Process trade cancellation.
     */
    private void processCancellation(FxMatrixFillMessage fill) {
        Optional<TradeExecution> existing = tradeExecutionRepository
                .findByFxMatrixRef(fill.getExecutionRef());
        
        if (existing.isPresent()) {
            TradeExecution execution = existing.get();
            execution.setStatus("CANCELLED");
            execution.setCancelledAt(LocalDateTime.now());
            tradeExecutionRepository.save(execution);
            
            log.info("Trade cancelled: {}", fill.getExecutionRef());
        } else {
            log.warn("No trade found for cancellation: {}", fill.getExecutionRef());
        }
    }

    /**
     * Find existing trade execution or create new one.
     */
    private TradeExecution findOrCreateExecution(FxMatrixFillMessage fill) {
        // Try to find by client order ref first
        if (fill.getClientOrderRef() != null) {
            Optional<TradeExecution> existing = tradeExecutionRepository
                    .findByClientOrderRef(fill.getClientOrderRef());
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        
        // Try to find by FXMatrix ref
        if (fill.getExecutionRef() != null) {
            Optional<TradeExecution> existing = tradeExecutionRepository
                    .findByFxMatrixRef(fill.getExecutionRef());
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        
        // Create new execution record
        Account account = null;
        if (fill.getAccountNumber() != null) {
            account = accountRepository.findByAccountNumber(fill.getAccountNumber()).orElse(null);
        }
        
        Counterparty counterparty = null;
        if (fill.getCounterparty() != null) {
            counterparty = counterpartyRepository.findByCounterpartyCode(fill.getCounterparty()).orElse(null);
        }
        
        return TradeExecution.builder()
                .account(account)
                .counterparty(counterparty)
                .clientOrderRef(fill.getClientOrderRef())
                .tradeType(fill.getTradeType())
                .buyCurrency(fill.getBuyCurrency())
                .sellCurrency(fill.getSellCurrency())
                .buyAmount(fill.getBuyAmount())
                .sellAmount(fill.getSellAmount())
                .sentAt(LocalDateTime.now())
                .sourceTab("UNKNOWN")
                .build();
    }

    /**
     * Create forward contract from executed forward trade.
     */
    private void createForwardContract(FxMatrixFillMessage fill, TradeExecution execution) {
        ForwardContract contract = ForwardContract.builder()
                .account(execution.getAccount())
                .counterparty(execution.getCounterparty())
                .tradeExecution(execution)
                .currencyPair(fill.getCurrencyPair())
                .buyCurrency(fill.getBuyCurrency())
                .sellCurrency(fill.getSellCurrency())
                .notionalAmount(fill.getBuyAmount())
                .contractRate(fill.getExecutionRate())
                .spotRateAtTrade(fill.getSpotRate())
                .forwardPoints(fill.getForwardPoints())
                .tradeDate(fill.getExecutionTime() != null ? fill.getExecutionTime().toLocalDate() : null)
                .valueDate(fill.getValueDate())
                .status("ACTIVE")
                .build();
        
        forwardContractRepository.save(contract);
        
        log.info("Created forward contract for {} maturing {}", 
                fill.getCurrencyPair(), fill.getValueDate());
    }

    /**
     * Publish processed trade event to Kafka.
     */
    private void publishProcessedEvent(FxMatrixFillMessage fill, TradeExecution execution) {
        TradeFillEvent event = TradeFillEvent.builder()
                .executionRef(fill.getExecutionRef())
                .clientOrderRef(fill.getClientOrderRef())
                .tradeType(fill.getTradeType())
                .buyCurrency(fill.getBuyCurrency())
                .sellCurrency(fill.getSellCurrency())
                .buyAmount(fill.getBuyAmount())
                .sellAmount(fill.getSellAmount())
                .executionRate(fill.getExecutionRate())
                .valueDate(fill.getValueDate())
                .status(execution.getStatus())
                .rejectReason(fill.getRejectReason())
                .processedAt(LocalDateTime.now())
                .build();
        
        try {
            kafkaTemplate.send(TradeFillEvent.TOPIC_PROCESSED, fill.getExecutionRef(), event);
        } catch (Exception e) {
            log.warn("Failed to publish trade processed event: {}", e.getMessage());
        }
    }

    /**
     * Get pending trades that may be stale.
     */
    public List<TradeExecution> getStalePendingTrades(int timeoutMinutes) {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(timeoutMinutes);
        return tradeExecutionRepository.findStalePendingTrades(cutoff);
    }

    /**
     * Mark stale pending trades as failed.
     */
    @Transactional
    public int markStalePendingTradesAsFailed(int timeoutMinutes) {
        List<TradeExecution> staleTrades = getStalePendingTrades(timeoutMinutes);
        
        for (TradeExecution trade : staleTrades) {
            trade.setStatus("FAILED");
            trade.setRejectReason("Timeout - no response from FXMatrix");
            trade.setRejectedAt(LocalDateTime.now());
            tradeExecutionRepository.save(trade);
            
            fillsFailedCounter.increment();
            log.warn("Marked trade {} as failed due to timeout", trade.getClientOrderRef());
        }
        
        return staleTrades.size();
    }

    /**
     * Get execution statistics.
     */
    public ExecutionStats getStats() {
        Double avgExecutionTime = tradeExecutionRepository.getAverageExecutionTimeSeconds();
        
        return ExecutionStats.builder()
                .totalProcessed((long) fillsProcessedCounter.count())
                .totalExecuted((long) fillsExecutedCounter.count())
                .totalRejected((long) fillsRejectedCounter.count())
                .totalFailed((long) fillsFailedCounter.count())
                .avgExecutionTimeSeconds(avgExecutionTime != null ? avgExecutionTime : 0.0)
                .build();
    }

    @lombok.Data
    @lombok.Builder
    public static class ExecutionStats {
        private long totalProcessed;
        private long totalExecuted;
        private long totalRejected;
        private long totalFailed;
        private double avgExecutionTimeSeconds;
    }
}
