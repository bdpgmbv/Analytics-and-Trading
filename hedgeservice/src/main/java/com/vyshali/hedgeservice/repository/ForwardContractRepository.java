package com.vyshali.hedgeservice.repository;

import com.fxanalyzer.hedgeservice.entity.ForwardContract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ForwardContractRepository extends JpaRepository<ForwardContract, Long> {
    
    /**
     * Get all active forward contracts for a portfolio.
     */
    @Query("""
        SELECT f FROM ForwardContract f
        WHERE f.portfolioId = :portfolioId
        AND f.status = 'ACTIVE'
        AND f.maturityDate >= :asOfDate
        ORDER BY f.maturityDate ASC
    """)
    List<ForwardContract> findActiveByPortfolio(
        @Param("portfolioId") Integer portfolioId,
        @Param("asOfDate") LocalDate asOfDate
    );
    
    /**
     * Get forward contracts maturing within specified days.
     */
    @Query("""
        SELECT f FROM ForwardContract f
        WHERE f.portfolioId = :portfolioId
        AND f.status = 'ACTIVE'
        AND f.maturityDate BETWEEN :asOfDate AND :maxDate
        ORDER BY f.maturityDate ASC
    """)
    List<ForwardContract> findMaturingWithinDays(
        @Param("portfolioId") Integer portfolioId,
        @Param("asOfDate") LocalDate asOfDate,
        @Param("maxDate") LocalDate maxDate
    );
    
    /**
     * Get forward contracts by currency pair.
     */
    @Query("""
        SELECT f FROM ForwardContract f
        WHERE f.portfolioId = :portfolioId
        AND f.buyCurrency = :buyCurrency
        AND f.sellCurrency = :sellCurrency
        AND f.status = 'ACTIVE'
        AND f.maturityDate >= :asOfDate
        ORDER BY f.maturityDate ASC
    """)
    List<ForwardContract> findByCurrencyPair(
        @Param("portfolioId") Integer portfolioId,
        @Param("buyCurrency") String buyCurrency,
        @Param("sellCurrency") String sellCurrency,
        @Param("asOfDate") LocalDate asOfDate
    );
    
    /**
     * Get forwards by trade execution reference.
     */
    @Query("""
        SELECT f FROM ForwardContract f
        WHERE f.tradeExecutionId = :tradeExecutionId
    """)
    List<ForwardContract> findByTradeExecutionId(@Param("tradeExecutionId") Long tradeExecutionId);
    
    /**
     * Get notional amount summary by maturity bucket.
     */
    @Query("""
        SELECT 
            CASE 
                WHEN f.maturityDate <= :criticalDate THEN 'CRITICAL'
                WHEN f.maturityDate <= :warningDate THEN 'WARNING'
                ELSE 'INFORMATIONAL'
            END as alertLevel,
            f.buyCurrency,
            SUM(f.notionalAmount) as totalNotional,
            COUNT(f) as contractCount
        FROM ForwardContract f
        WHERE f.portfolioId = :portfolioId
        AND f.status = 'ACTIVE'
        AND f.maturityDate BETWEEN :asOfDate AND :maxDate
        GROUP BY alertLevel, f.buyCurrency
    """)
    List<Object[]> findMaturitySummary(
        @Param("portfolioId") Integer portfolioId,
        @Param("asOfDate") LocalDate asOfDate,
        @Param("criticalDate") LocalDate criticalDate,
        @Param("warningDate") LocalDate warningDate,
        @Param("maxDate") LocalDate maxDate
    );
}
