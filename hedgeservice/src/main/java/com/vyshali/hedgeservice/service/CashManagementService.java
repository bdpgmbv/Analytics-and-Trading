package com.vyshali.hedgeservice.service;

import com.fxanalyzer.hedgeservice.dto.CashManagementDto;
import com.fxanalyzer.hedgeservice.dto.CashManagementDto.CashBalance;
import com.fxanalyzer.hedgeservice.dto.CashManagementDto.CashFlow;
import com.fxanalyzer.hedgeservice.repository.CashBalanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CashManagementService {
    
    private final CashBalanceRepository cashBalanceRepository;
    private final PriceService priceService;
    
    /**
     * Get cash management data for a portfolio.
     * Tab 4: Cash Management - Currency cash balances and cash flows.
     */
    @Cacheable(value = "cash-management", key = "#portfolioId + '_' + #asOfDate")
    @Transactional(readOnly = true)
    public CashManagementDto getCashManagement(Integer portfolioId, LocalDate asOfDate) {
        log.info("Getting cash management for portfolio {} as of {}", portfolioId, asOfDate);
        
        List<com.fxanalyzer.hedgeservice.entity.CashBalance> cashBalances = 
            cashBalanceRepository.findByPortfolioAndDate(portfolioId, asOfDate);
        
        if (cashBalances.isEmpty()) {
            log.warn("No cash balances found for portfolio {} as of {}", portfolioId, asOfDate);
            return createEmptyCashManagement(portfolioId, asOfDate);
        }
        
        // Convert to DTOs
        List<CashBalance> cashBalanceDtos = cashBalances.stream()
            .map(this::toCashBalanceDto)
            .toList();
        
        // Get projected cash flows (would typically come from trade settlements, dividends, etc.)
        List<CashFlow> projectedCashFlows = getProjectedCashFlows(portfolioId, asOfDate);
        
        // Calculate summary
        BigDecimal totalCashBase = cashBalances.stream()
            .map(com.fxanalyzer.hedgeservice.entity.CashBalance::getBalanceBase)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal projectedNetCashFlow7Days = calculateNetCashFlow(projectedCashFlows, asOfDate, 7);
        BigDecimal projectedNetCashFlow30Days = calculateNetCashFlow(projectedCashFlows, asOfDate, 30);
        
        String portfolioName = cashBalances.get(0).getPortfolio().getFund().getFundName();
        String baseCurrency = cashBalances.get(0).getPortfolio().getFund().getBaseCurrency();
        
        return new CashManagementDto(
            portfolioId,
            portfolioName,
            baseCurrency,
            asOfDate,
            cashBalanceDtos,
            projectedCashFlows,
            totalCashBase,
            projectedNetCashFlow7Days,
            projectedNetCashFlow30Days,
            LocalDateTime.now()
        );
    }
    
    /**
     * Get overdraft cash balances.
     */
    @Transactional(readOnly = true)
    public List<CashBalance> getOverdrafts(Integer portfolioId, LocalDate asOfDate) {
        List<com.fxanalyzer.hedgeservice.entity.CashBalance> overdrafts = 
            cashBalanceRepository.findOverdrafts(portfolioId, asOfDate);
        
        return overdrafts.stream()
            .map(this::toCashBalanceDto)
            .toList();
    }
    
    /**
     * Convert CashBalance entity to DTO.
     */
    private CashBalance toCashBalanceDto(com.fxanalyzer.hedgeservice.entity.CashBalance entity) {
        BigDecimal fxRate = priceService.getFxRate(entity.getCurrency(), entity.getAsOfDate());
        
        // Calculate day change (would compare to previous day's balance)
        BigDecimal dayChangeAmount = BigDecimal.ZERO; // Placeholder
        BigDecimal dayChangePercent = BigDecimal.ZERO; // Placeholder
        
        boolean isOverdrawn = entity.getBalance().compareTo(BigDecimal.ZERO) < 0;
        boolean breachesLimit = false; // Would check against configured limits
        
        return new CashBalance(
            entity.getCurrency(),
            entity.getBalance(),
            entity.getBalanceBase(),
            fxRate,
            entity.getAccount() != null ? entity.getAccount().getAccountNumber() : "N/A",
            dayChangeAmount,
            dayChangePercent,
            BigDecimal.ZERO, // Interest rate - placeholder
            BigDecimal.ZERO, // Accrued interest - placeholder
            BigDecimal.ZERO, // Min balance required - placeholder
            BigDecimal.ZERO, // Max balance allowed - placeholder
            isOverdrawn,
            breachesLimit
        );
    }
    
    /**
     * Get projected cash flows from various sources.
     */
    private List<CashFlow> getProjectedCashFlows(Integer portfolioId, LocalDate asOfDate) {
        List<CashFlow> cashFlows = new ArrayList<>();
        
        // In a real system, this would query:
        // - Pending trade settlements
        // - Expected dividends
        // - Forward contract settlements
        // - Redemptions/Subscriptions
        // - Coupon payments
        
        // Placeholder - return empty list
        return cashFlows;
    }
    
    /**
     * Calculate net cash flow within specified days.
     */
    private BigDecimal calculateNetCashFlow(List<CashFlow> cashFlows, LocalDate fromDate, int days) {
        LocalDate toDate = fromDate.plusDays(days);
        
        return cashFlows.stream()
            .filter(cf -> !cf.valueDate().isBefore(fromDate) && !cf.valueDate().isAfter(toDate))
            .map(CashFlow::amountBase)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    /**
     * Create empty cash management response.
     */
    private CashManagementDto createEmptyCashManagement(Integer portfolioId, LocalDate asOfDate) {
        return new CashManagementDto(
            portfolioId,
            "Unknown Portfolio",
            "USD",
            asOfDate,
            List.of(),
            List.of(),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            LocalDateTime.now()
        );
    }
}
