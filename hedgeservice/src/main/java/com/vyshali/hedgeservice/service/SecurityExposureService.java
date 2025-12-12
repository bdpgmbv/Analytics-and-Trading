package com.vyshali.hedgeservice.service;

import com.fxanalyzer.hedgeservice.dto.SecurityExposureDto;
import com.fxanalyzer.hedgeservice.dto.SecurityExposureDto.CurrencyExposure;
import com.fxanalyzer.hedgeservice.dto.SecurityExposureDto.PositionDetail;
import com.fxanalyzer.hedgeservice.entity.Position;
import com.fxanalyzer.hedgeservice.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityExposureService {
    
    private final PositionRepository positionRepository;
    private final PriceService priceService;
    
    /**
     * Get security exposure for a portfolio.
     * Tab 1: Real-time currency exposures and P/L.
     */
    @Cacheable(value = "security-exposures", key = "#portfolioId + '_' + #asOfDate")
    @Transactional(readOnly = true)
    public SecurityExposureDto getSecurityExposure(Integer portfolioId, LocalDate asOfDate) {
        log.info("Getting security exposure for portfolio {} as of {}", portfolioId, asOfDate);
        
        // Get all positions for the portfolio
        List<Position> positions = positionRepository.findByPortfolioAndDate(portfolioId, asOfDate);
        
        if (positions.isEmpty()) {
            log.warn("No positions found for portfolio {} as of {}", portfolioId, asOfDate);
            return createEmptyExposure(portfolioId, asOfDate);
        }
        
        // Group positions by currency
        Map<String, List<Position>> positionsByCurrency = positions.stream()
            .collect(Collectors.groupingBy(p -> p.getProduct().getCurrency()));
        
        // Build currency exposures
        List<CurrencyExposure> currencyExposures = positionsByCurrency.entrySet().stream()
            .map(entry -> buildCurrencyExposure(entry.getKey(), entry.getValue()))
            .sorted((a, b) -> b.marketValueBase().abs().compareTo(a.marketValueBase().abs()))
            .toList();
        
        // Calculate totals
        BigDecimal totalMarketValue = currencyExposures.stream()
            .map(CurrencyExposure::marketValueBase)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal totalUnrealizedPnl = currencyExposures.stream()
            .map(CurrencyExposure::unrealizedPnl)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal totalRealizedPnl = currencyExposures.stream()
            .map(CurrencyExposure::realizedPnl)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        String portfolioName = positions.get(0).getSnapshot().getPortfolio().getFund().getFundName();
        String baseCurrency = positions.get(0).getSnapshot().getPortfolio().getFund().getBaseCurrency();
        
        return new SecurityExposureDto(
            portfolioId,
            portfolioName,
            baseCurrency,
            asOfDate,
            currencyExposures,
            totalMarketValue,
            totalUnrealizedPnl,
            totalRealizedPnl,
            LocalDateTime.now()
        );
    }
    
    /**
     * Build currency exposure from positions.
     */
    private CurrencyExposure buildCurrencyExposure(String currency, List<Position> positions) {
        BigDecimal marketValue = positions.stream()
            .map(Position::getMarketValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal marketValueBase = positions.stream()
            .map(Position::getMarketValueBase)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal quantity = positions.stream()
            .map(Position::getQuantity)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal unrealizedPnl = positions.stream()
            .map(Position::getUnrealizedPnl)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal realizedPnl = positions.stream()
            .map(Position::getRealizedPnl)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Get FX rate for the currency
        BigDecimal fxRate = priceService.getFxRate(currency, positions.get(0).getSnapshot().getSnapshotDate());
        
        // Calculate net exposure (market value in base currency)
        BigDecimal netExposure = marketValueBase;
        
        // Hedge ratio (would come from hedge positions - placeholder for now)
        BigDecimal hedgeRatio = BigDecimal.ZERO;
        
        // Build position details
        List<PositionDetail> positionDetails = positions.stream()
            .map(this::toPositionDetail)
            .toList();
        
        return new CurrencyExposure(
            currency,
            marketValue,
            marketValueBase,
            quantity,
            fxRate,
            unrealizedPnl,
            realizedPnl,
            netExposure,
            hedgeRatio,
            positionDetails
        );
    }
    
    /**
     * Convert Position entity to PositionDetail DTO.
     */
    private PositionDetail toPositionDetail(Position position) {
        return new PositionDetail(
            position.getPositionId(),
            position.getProduct().getTicker(),
            position.getProduct().getSecurityDescription(),
            position.getProduct().getAssetClass(),
            position.getQuantity(),
            position.getPrice(),
            position.getMarketValue(),
            position.getMarketValueBase(),
            position.getUnrealizedPnl(),
            position.getCostBasis(),
            position.getAccount().getAccountNumber()
        );
    }
    
    /**
     * Create empty exposure response.
     */
    private SecurityExposureDto createEmptyExposure(Integer portfolioId, LocalDate asOfDate) {
        return new SecurityExposureDto(
            portfolioId,
            "Unknown Portfolio",
            "USD",
            asOfDate,
            List.of(),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            LocalDateTime.now()
        );
    }
}
