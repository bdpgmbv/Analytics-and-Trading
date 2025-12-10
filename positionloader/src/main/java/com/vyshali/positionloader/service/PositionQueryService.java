package com.vyshali.positionloader.service;

/*
 * 12/10/2025 - NEW: Service layer for position queries
 *
 * PURPOSE:
 * This service provides query operations for positions, maintaining the
 * architecture rule that controllers should not directly access repositories.
 *
 * ARCHITECTURE FIX:
 * - OpsController was directly using PositionRepository (violation)
 * - Now OpsController uses this service instead
 * - This maintains: Controller → Service → Repository pattern
 *
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.positionloader.dto.PositionDTO;
import com.vyshali.positionloader.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for position query operations.
 * Provides a clean API for controllers to query positions without
 * violating the layered architecture.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PositionQueryService {

    private final PositionRepository positionRepository;

    /**
     * Get position quantity as of a specific point in time (bitemporal query).
     *
     * @param accountId    The account ID
     * @param productId    The product ID
     * @param businessDate The business date we're asking about
     * @param systemTime   The system time (when did we know this?)
     * @return The quantity as of that point in time, or ZERO if not found
     */
    public BigDecimal getQuantityAsOf(Integer accountId, Integer productId, LocalDateTime businessDate, LocalDateTime systemTime) {
        log.debug("Querying position quantity: account={}, product={}, businessDate={}, systemTime={}", accountId, productId, businessDate, systemTime);

        Timestamp businessTs = Timestamp.valueOf(businessDate);
        Timestamp systemTs = Timestamp.valueOf(systemTime);

        return positionRepository.getQuantityAsOf(accountId, productId, businessTs, systemTs);
    }

    /**
     * Get all positions for an account as of a specific business date.
     *
     * @param accountId    The account ID
     * @param businessDate The business date
     * @return List of positions as of that date
     */
    public List<PositionDTO> getPositionsAsOf(Integer accountId, LocalDate businessDate) {
        log.debug("Querying positions: account={}, date={}", accountId, businessDate);
        return positionRepository.getPositionsAsOf(accountId, businessDate);
    }

    /**
     * Get current positions for an account.
     *
     * @param accountId The account ID
     * @return Count of current positions
     */
    public int countCurrentPositions(Integer accountId) {
        return positionRepository.countPositions(accountId);
    }

    /**
     * Get the active batch ID for an account.
     *
     * @param accountId The account ID
     * @return The active batch ID, or null if none
     */
    public Integer getActiveBatchId(Integer accountId) {
        return positionRepository.getActiveBatchId(accountId);
    }
}