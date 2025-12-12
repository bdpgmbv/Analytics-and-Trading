package com.vyshali.hedgeservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketPublisher {
    
    private final SimpMessagingTemplate messagingTemplate;
    
    /**
     * Publish position update to clients.
     */
    public void publishPositionUpdate(Integer portfolioId, PositionUpdateMessage message) {
        String destination = "/topic/positions/" + portfolioId;
        log.debug("Publishing position update to {}", destination);
        messagingTemplate.convertAndSend(destination, message);
    }
    
    /**
     * Publish price update to clients.
     */
    public void publishPriceUpdate(String ticker, PriceUpdateMessage message) {
        String destination = "/topic/prices/" + ticker;
        log.debug("Publishing price update to {}", destination);
        messagingTemplate.convertAndSend(destination, message);
    }
    
    /**
     * Publish transaction update to clients.
     */
    public void publishTransactionUpdate(Integer portfolioId, TransactionUpdateMessage message) {
        String destination = "/topic/transactions/" + portfolioId;
        log.debug("Publishing transaction update to {}", destination);
        messagingTemplate.convertAndSend(destination, message);
    }
    
    /**
     * Publish forward alert to clients.
     */
    public void publishForwardAlert(Integer portfolioId, ForwardAlertMessage message) {
        String destination = "/topic/alerts/" + portfolioId;
        log.debug("Publishing forward alert to {}", destination);
        messagingTemplate.convertAndSend(destination, message);
    }
    
    /**
     * Position update message.
     */
    public record PositionUpdateMessage(
        Integer portfolioId,
        Long positionId,
        String ticker,
        java.math.BigDecimal quantity,
        java.math.BigDecimal marketValue,
        String updateType, // CREATED, UPDATED, DELETED
        LocalDateTime timestamp
    ) {}
    
    /**
     * Price update message.
     */
    public record PriceUpdateMessage(
        String ticker,
        java.math.BigDecimal price,
        java.math.BigDecimal bidPrice,
        java.math.BigDecimal askPrice,
        String source,
        LocalDateTime timestamp
    ) {}
    
    /**
     * Transaction update message.
     */
    public record TransactionUpdateMessage(
        Integer portfolioId,
        Long transactionId,
        String transactionType,
        String ticker,
        String status,
        LocalDateTime timestamp
    ) {}
    
    /**
     * Forward alert message.
     */
    public record ForwardAlertMessage(
        Integer portfolioId,
        Long forwardContractId,
        String alertLevel,
        int daysToMaturity,
        String message,
        LocalDateTime timestamp
    ) {}
}
