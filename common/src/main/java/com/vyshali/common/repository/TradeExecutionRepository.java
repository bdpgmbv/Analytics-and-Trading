package com.vyshali.common.repository;

import com.vyshali.fxanalyzer.common.entity.TradeExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for trade execution tracking.
 * Addresses Issue #2: No tracking for executed trades (sent vs executed)
 */
@Repository
public interface TradeExecutionRepository extends JpaRepository<TradeExecution, Long> {

    Optional<TradeExecution> findByExecutionRef(String executionRef);
    
    List<TradeExecution> findByAccountAccountId(Long accountId);
    
    List<TradeExecution> findByStatus(String status);
    
    /**
     * Find trades pending execution (sent but not yet confirmed)
     */
    @Query("SELECT t FROM TradeExecution t WHERE t.status = 'SENT' ORDER BY t.sentAt ASC")
    List<TradeExecution> findPendingExecutions();
    
    /**
     * Find failed or rejected trades that need attention
     */
    @Query("SELECT t FROM TradeExecution t WHERE t.status IN ('REJECTED', 'FAILED') ORDER BY t.sentAt DESC")
    List<TradeExecution> findFailedExecutions();
    
    /**
     * Find trades sent within a time window
     */
    @Query("SELECT t FROM TradeExecution t WHERE t.sentAt BETWEEN :startTime AND :endTime")
    List<TradeExecution> findBySentAtBetween(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);
    
    /**
     * Find today's executions for an account
     */
    @Query("SELECT t FROM TradeExecution t WHERE t.account.accountId = :accountId " +
           "AND CAST(t.sentAt AS date) = CURRENT_DATE ORDER BY t.sentAt DESC")
    List<TradeExecution> findTodaysExecutions(@Param("accountId") Long accountId);
    
    /**
     * Find by value date
     */
    List<TradeExecution> findByValueDate(LocalDate valueDate);
    
    /**
     * Count by status
     */
    @Query("SELECT t.status, COUNT(t) FROM TradeExecution t GROUP BY t.status")
    List<Object[]> countByStatus();
    
    /**
     * Find stale pending trades (sent more than X minutes ago)
     */
    @Query("SELECT t FROM TradeExecution t WHERE t.status = 'SENT' AND t.sentAt < :cutoff")
    List<TradeExecution> findStalePendingTrades(@Param("cutoff") LocalDateTime cutoff);
    
    /**
     * Find executions by source tab (to identify which UI feature originated the trade)
     */
    List<TradeExecution> findBySourceTab(String sourceTab);
    
    /**
     * Average execution time for successful trades
     */
    @Query("SELECT AVG(TIMESTAMPDIFF(SECOND, t.sentAt, t.executedAt)) FROM TradeExecution t " +
           "WHERE t.status = 'EXECUTED' AND t.executedAt IS NOT NULL")
    Double getAverageExecutionTimeSeconds();
}
