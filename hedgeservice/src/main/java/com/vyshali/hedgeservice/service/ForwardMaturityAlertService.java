package com.vyshali.hedgeservice.service;

import com.fxanalyzer.hedgeservice.dto.ForwardMaturityAlertDto;
import com.fxanalyzer.hedgeservice.dto.ForwardMaturityAlertDto.AlertLevel;
import com.fxanalyzer.hedgeservice.dto.ForwardMaturityAlertDto.ForwardAlert;
import com.fxanalyzer.hedgeservice.entity.ForwardContract;
import com.fxanalyzer.hedgeservice.repository.ForwardContractRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ForwardMaturityAlertService {
    
    private final ForwardContractRepository forwardContractRepository;
    private final PriceService priceService;
    
    @Value("${fx-analyzer.alerts.forward-maturity-days:7}")
    private int defaultAlertDays;
    
    /**
     * Get forward maturity alerts for a portfolio.
     * Tab 5: Forward Maturity Alert - Alerts for maturing forward contracts.
     */
    @Cacheable(value = "forward-alerts", key = "#portfolioId + '_' + #asOfDate")
    @Transactional(readOnly = true)
    public ForwardMaturityAlertDto getForwardMaturityAlerts(Integer portfolioId, LocalDate asOfDate) {
        log.info("Getting forward maturity alerts for portfolio {} as of {}", portfolioId, asOfDate);
        
        // Get all forward contracts maturing within 30 days
        LocalDate maxAlertDate = asOfDate.plusDays(30);
        List<ForwardContract> maturingForwards = forwardContractRepository.findMaturingWithinDays(
            portfolioId,
            asOfDate,
            maxAlertDate
        );
        
        if (maturingForwards.isEmpty()) {
            log.info("No maturing forwards found for portfolio {}", portfolioId);
            return createEmptyAlerts(portfolioId, asOfDate);
        }
        
        // Categorize alerts by urgency
        List<ForwardAlert> criticalAlerts = new ArrayList<>();
        List<ForwardAlert> warningAlerts = new ArrayList<>();
        List<ForwardAlert> informationalAlerts = new ArrayList<>();
        
        for (ForwardContract contract : maturingForwards) {
            ForwardAlert alert = buildForwardAlert(contract, asOfDate);
            
            switch (alert.alertLevel()) {
                case CRITICAL -> criticalAlerts.add(alert);
                case WARNING -> warningAlerts.add(alert);
                case INFORMATIONAL -> informationalAlerts.add(alert);
            }
        }
        
        // Calculate summary metrics
        BigDecimal totalNotional7Days = maturingForwards.stream()
            .filter(f -> ChronoUnit.DAYS.between(asOfDate, f.getMaturityDate()) <= 7)
            .map(ForwardContract::getNotionalAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal totalNotional30Days = maturingForwards.stream()
            .map(ForwardContract::getNotionalAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        String portfolioName = maturingForwards.get(0).getPortfolio().getFund().getFundName();
        
        return new ForwardMaturityAlertDto(
            portfolioId,
            portfolioName,
            asOfDate,
            criticalAlerts,
            warningAlerts,
            informationalAlerts,
            maturingForwards.size(),
            totalNotional7Days,
            totalNotional30Days,
            LocalDateTime.now()
        );
    }
    
    /**
     * Build forward alert from contract.
     */
    private ForwardAlert buildForwardAlert(ForwardContract contract, LocalDate asOfDate) {
        long daysToMaturity = ChronoUnit.DAYS.between(asOfDate, contract.getMaturityDate());
        
        // Determine alert level
        AlertLevel alertLevel;
        if (daysToMaturity <= 2) {
            alertLevel = AlertLevel.CRITICAL;
        } else if (daysToMaturity <= 7) {
            alertLevel = AlertLevel.WARNING;
        } else {
            alertLevel = AlertLevel.INFORMATIONAL;
        }
        
        // Get current spot rate for MTM calculation
        BigDecimal spotRate = priceService.getFxRate(
            contract.getBuyCurrency(), 
            contract.getSellCurrency(), 
            asOfDate
        );
        
        // Calculate mark-to-market P/L
        BigDecimal currentMtmPnl = calculateMtmPnl(contract, spotRate);
        
        // Determine recommended action
        String recommendedAction = determineRecommendedAction(contract, daysToMaturity);
        
        return new ForwardAlert(
            contract.getForwardContractId(),
            contract.getExternalRef(),
            alertLevel,
            (int) daysToMaturity,
            contract.getTradeDate(),
            contract.getMaturityDate(),
            contract.getBuyCurrency(),
            contract.getSellCurrency(),
            contract.getNotionalAmount(),
            contract.getForwardRate(),
            spotRate,
            currentMtmPnl,
            contract.getAccount() != null ? contract.getAccount().getAccountNumber() : null,
            contract.getNotionalAmount(), // Settlement amount buy
            contract.getNotionalAmount().multiply(contract.getForwardRate()), // Settlement amount sell
            contract.getCounterparty() != null ? contract.getCounterparty().getCounterpartyName() : null,
            recommendedAction,
            false, // Auto-roll would be configured per contract
            null, // Related hedge reference
            null // Notes
        );
    }
    
    /**
     * Calculate mark-to-market P/L for forward contract.
     */
    private BigDecimal calculateMtmPnl(ForwardContract contract, BigDecimal spotRate) {
        // Simplified MTM calculation: (Spot Rate - Forward Rate) * Notional
        // Positive = profit, Negative = loss
        BigDecimal rateDiff = spotRate.subtract(contract.getForwardRate());
        return rateDiff.multiply(contract.getNotionalAmount());
    }
    
    /**
     * Determine recommended action based on days to maturity.
     */
    private String determineRecommendedAction(ForwardContract contract, long daysToMaturity) {
        if (daysToMaturity <= 2) {
            return "SETTLE"; // Critical - must settle or roll immediately
        } else if (daysToMaturity <= 7) {
            return "ROLL_FORWARD"; // Warning - consider rolling forward
        } else {
            return "MONITOR"; // Informational - continue monitoring
        }
    }
    
    /**
     * Create empty alerts response.
     */
    private ForwardMaturityAlertDto createEmptyAlerts(Integer portfolioId, LocalDate asOfDate) {
        return new ForwardMaturityAlertDto(
            portfolioId,
            "Unknown Portfolio",
            asOfDate,
            List.of(),
            List.of(),
            List.of(),
            0,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            LocalDateTime.now()
        );
    }
}
