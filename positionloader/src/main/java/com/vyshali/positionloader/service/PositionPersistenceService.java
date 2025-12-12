package com.vyshali.positionloader.service;

import com.vyshali.fxanalyzer.common.entity.*;
import com.vyshali.fxanalyzer.common.exception.EntityNotFoundException;
import com.vyshali.fxanalyzer.common.repository.*;
import com.vyshali.fxanalyzer.common.util.CalculationUtil;
import com.vyshali.fxanalyzer.positionloader.dto.MspmPositionMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service for persisting positions from MSPM messages.
 * Handles product lookup/creation and exposure calculation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PositionPersistenceService {

    private final PositionRepository positionRepository;
    private final ProductRepository productRepository;
    private final AccountRepository accountRepository;
    private final ExposureRepository exposureRepository;

    /**
     * Persist positions from MSPM message to database.
     * Returns the count of positions saved.
     */
    @Transactional
    public int persistPositions(Snapshot snapshot, List<MspmPositionMessage.PositionData> positionDataList) {
        
        if (positionDataList == null || positionDataList.isEmpty()) {
            log.warn("No positions to persist for snapshot {}", snapshot.getSnapshotId());
            return 0;
        }
        
        Account account = snapshot.getAccount();
        List<Position> positions = new ArrayList<>();
        BigDecimal totalMvBase = BigDecimal.ZERO;
        
        for (MspmPositionMessage.PositionData data : positionDataList) {
            try {
                Position position = createPosition(snapshot, account, data);
                positions.add(position);
                
                if (position.getMarketValueBase() != null) {
                    totalMvBase = totalMvBase.add(position.getMarketValueBase());
                }
            } catch (Exception e) {
                log.error("Failed to create position for {}/{}: {}", 
                        data.getIdentifierType(), data.getIdentifier(), e.getMessage());
            }
        }
        
        // Batch save positions
        List<Position> savedPositions = positionRepository.saveAll(positions);
        log.info("Saved {} positions for snapshot {}", savedPositions.size(), snapshot.getSnapshotId());
        
        // Update snapshot totals
        snapshot.setPositionCount(savedPositions.size());
        snapshot.setTotalMvBase(totalMvBase);
        
        return savedPositions.size();
    }

    /**
     * Create a Position entity from MSPM position data.
     */
    private Position createPosition(Snapshot snapshot, Account account, MspmPositionMessage.PositionData data) {
        
        // Find or create product
        Product product = findOrCreateProduct(data);
        
        // Calculate unrealized P&L if not provided
        BigDecimal unrealizedPnlLocal = CalculationUtil.subtract(
                data.getMarketValueLocal(), data.getCostBasisLocal());
        BigDecimal unrealizedPnlBase = CalculationUtil.subtract(
                data.getMarketValueBase(), data.getCostBasisBase());
        
        Position position = Position.builder()
                .snapshot(snapshot)
                .account(account)
                .product(product)
                .quantity(data.getQuantity())
                .costBasisLocal(data.getCostBasisLocal())
                .costBasisBase(data.getCostBasisBase())
                .marketValueLocal(data.getMarketValueLocal())
                .marketValueBase(data.getMarketValueBase())
                .unrealizedPnlLocal(unrealizedPnlLocal)
                .unrealizedPnlBase(unrealizedPnlBase)
                .priceUsed(data.getPrice())
                .fxRateUsed(data.getFxRate())
                .positionType(data.getPositionType())
                .sourceSystem("MSPM")
                .isExcluded(false)
                .build();
        
        // Add exposures
        if (data.getExposures() != null && !data.getExposures().isEmpty()) {
            for (MspmPositionMessage.ExposureData expData : data.getExposures()) {
                Exposure exposure = createExposure(position, product, expData);
                position.addExposure(exposure);
            }
        } else {
            // Create default generic exposure based on issue currency
            createDefaultExposure(position, product);
        }
        
        return position;
    }

    /**
     * Find existing product or create a new one.
     */
    private Product findOrCreateProduct(MspmPositionMessage.PositionData data) {
        Optional<Product> existing = productRepository.findByIdentifierTypeAndIdentifier(
                data.getIdentifierType(), data.getIdentifier());
        
        if (existing.isPresent()) {
            return existing.get();
        }
        
        // Create new product
        Product product = Product.builder()
                .identifierType(data.getIdentifierType())
                .identifier(data.getIdentifier())
                .ticker(data.getTicker())
                .securityDescription(data.getSecurityDescription())
                .assetClass(data.getAssetClass() != null ? data.getAssetClass() : "EQUITY")
                .issueCurrency(data.getIssueCurrency())
                .settlementCurrency(data.getSettlementCurrency() != null ? 
                        data.getSettlementCurrency() : data.getIssueCurrency())
                .isActive(true)
                .build();
        
        product = productRepository.save(product);
        log.info("Created new product: {}/{} - {}", 
                product.getIdentifierType(), product.getIdentifier(), product.getTicker());
        
        return product;
    }

    /**
     * Create an Exposure entity from exposure data.
     */
    private Exposure createExposure(Position position, Product product, MspmPositionMessage.ExposureData data) {
        
        BigDecimal exposureAmountLocal = CalculationUtil.calculateExposureAmount(
                position.getMarketValueLocal(), data.getWeightPercent());
        BigDecimal exposureAmountBase = CalculationUtil.calculateExposureAmount(
                position.getMarketValueBase(), data.getWeightPercent());
        
        return Exposure.builder()
                .position(position)
                .product(product)
                .exposureType(data.getExposureType())
                .currency(data.getCurrency())
                .weightPercent(data.getWeightPercent())
                .exposureAmountLocal(exposureAmountLocal)
                .exposureAmountBase(exposureAmountBase)
                .build();
    }

    /**
     * Create default generic exposure based on issue currency (100% weight).
     */
    private void createDefaultExposure(Position position, Product product) {
        Exposure exposure = Exposure.builder()
                .position(position)
                .product(product)
                .exposureType("GENERIC")
                .currency(product.getIssueCurrency())
                .weightPercent(BigDecimal.valueOf(100))
                .exposureAmountLocal(position.getMarketValueLocal())
                .exposureAmountBase(position.getMarketValueBase())
                .build();
        
        position.addExposure(exposure);
    }

    /**
     * Delete all positions for a snapshot (for reprocessing).
     */
    @Transactional
    public int deletePositionsForSnapshot(Long snapshotId) {
        List<Position> positions = positionRepository.findBySnapshotSnapshotId(snapshotId);
        positionRepository.deleteAll(positions);
        log.info("Deleted {} positions for snapshot {}", positions.size(), snapshotId);
        return positions.size();
    }
}
