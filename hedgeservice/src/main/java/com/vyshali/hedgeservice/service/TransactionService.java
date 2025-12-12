package com.vyshali.hedgeservice.service;

import com.fxanalyzer.hedgeservice.dto.TransactionDto;
import com.fxanalyzer.hedgeservice.dto.TransactionDto.TransactionSummary;
import com.fxanalyzer.hedgeservice.entity.Transaction;
import com.fxanalyzer.hedgeservice.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {
    
    private final TransactionRepository transactionRepository;
    
    /**
     * Get current day transactions for a portfolio.
     * Tab 2: Transactions - Current day transactions.
     */
    @Cacheable(value = "transactions", key = "#portfolioId + '_' + #transactionDate")
    @Transactional(readOnly = true)
    public TransactionSummary getCurrentDayTransactions(Integer portfolioId, LocalDate transactionDate) {
        log.info("Getting transactions for portfolio {} on {}", portfolioId, transactionDate);
        
        List<Transaction> transactions = transactionRepository.findByPortfolioAndDate(
            portfolioId, 
            transactionDate
        );
        
        List<TransactionDto> transactionDtos = transactions.stream()
            .map(this::toDto)
            .toList();
        
        // Calculate totals
        BigDecimal totalBuyAmount = transactions.stream()
            .filter(t -> "BUY".equals(t.getTransactionType()) || "FX_HEDGE_BUY".equals(t.getTransactionType()))
            .map(Transaction::getAmountBase)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal totalSellAmount = transactions.stream()
            .filter(t -> "SELL".equals(t.getTransactionType()) || "FX_HEDGE_SELL".equals(t.getTransactionType()))
            .map(Transaction::getAmountBase)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal netCashFlow = totalSellAmount.subtract(totalBuyAmount);
        
        String portfolioName = transactions.isEmpty() ? "Unknown" : 
            transactions.get(0).getPortfolio().getFund().getFundName();
        
        return new TransactionSummary(
            portfolioId,
            transactionDate,
            transactionDtos,
            totalBuyAmount,
            totalSellAmount,
            netCashFlow,
            transactions.size(),
            LocalDateTime.now()
        );
    }
    
    /**
     * Get transaction by ID.
     */
    @Transactional(readOnly = true)
    public TransactionDto getTransactionById(Long transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
            .orElseThrow(() -> new RuntimeException("Transaction not found: " + transactionId));
        return toDto(transaction);
    }
    
    /**
     * Get transactions for date range.
     */
    @Transactional(readOnly = true)
    public List<TransactionDto> getTransactionsByDateRange(
        Integer portfolioId,
        LocalDate startDate,
        LocalDate endDate
    ) {
        List<Transaction> transactions = transactionRepository.findByPortfolioAndDateRange(
            portfolioId, 
            startDate, 
            endDate
        );
        
        return transactions.stream()
            .map(this::toDto)
            .toList();
    }
    
    /**
     * Get pending transactions.
     */
    @Transactional(readOnly = true)
    public List<TransactionDto> getPendingTransactions(Integer portfolioId) {
        List<Transaction> transactions = transactionRepository.findPendingTransactions(portfolioId);
        
        return transactions.stream()
            .map(this::toDto)
            .toList();
    }
    
    /**
     * Convert Transaction entity to DTO.
     */
    private TransactionDto toDto(Transaction transaction) {
        return new TransactionDto(
            transaction.getTransactionId(),
            transaction.getPortfolioId(),
            transaction.getPortfolio().getFund().getFundName(),
            transaction.getTransactionDate(),
            transaction.getTransactionType(),
            transaction.getProduct() != null ? transaction.getProduct().getTicker() : null,
            transaction.getProduct() != null ? transaction.getProduct().getSecurityDescription() : null,
            transaction.getCurrency(),
            transaction.getQuantity(),
            transaction.getPrice(),
            transaction.getAmount(),
            transaction.getAmountBase(),
            transaction.getFxRate(),
            transaction.getAccount() != null ? transaction.getAccount().getAccountNumber() : null,
            transaction.getCounterparty() != null ? transaction.getCounterparty().getCounterpartyName() : null,
            transaction.getStatus(),
            transaction.getExternalRef(),
            transaction.getCreatedAt(),
            transaction.getExecutedAt()
        );
    }
}
