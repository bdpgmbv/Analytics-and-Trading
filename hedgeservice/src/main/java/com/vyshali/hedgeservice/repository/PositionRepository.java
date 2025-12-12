package com.vyshali.hedgeservice.repository;

import com.fxanalyzer.hedgeservice.entity.Position;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PositionRepository extends JpaRepository<Position, Long> {
    
    /**
     * Get all positions for a portfolio on a specific date.
     */
    @Query("""
        SELECT p FROM Position p
        JOIN p.snapshot s
        WHERE s.portfolioId = :portfolioId
        AND s.snapshotDate = :asOfDate
        AND p.quantity <> 0
        ORDER BY p.product.currency, p.product.ticker
    """)
    List<Position> findByPortfolioAndDate(
        @Param("portfolioId") Integer portfolioId,
        @Param("asOfDate") LocalDate asOfDate
    );
    
    /**
     * Get latest positions for a portfolio.
     */
    @Query("""
        SELECT p FROM Position p
        JOIN p.snapshot s
        WHERE s.portfolioId = :portfolioId
        AND s.snapshotDate = (
            SELECT MAX(s2.snapshotDate) 
            FROM Snapshot s2 
            WHERE s2.portfolioId = :portfolioId
        )
        AND p.quantity <> 0
        ORDER BY p.product.currency, p.product.ticker
    """)
    List<Position> findLatestByPortfolio(@Param("portfolioId") Integer portfolioId);
    
    /**
     * Get positions by currency for a portfolio.
     */
    @Query("""
        SELECT p FROM Position p
        JOIN p.snapshot s
        WHERE s.portfolioId = :portfolioId
        AND s.snapshotDate = :asOfDate
        AND p.product.currency = :currency
        AND p.quantity <> 0
        ORDER BY p.product.ticker
    """)
    List<Position> findByPortfolioCurrencyAndDate(
        @Param("portfolioId") Integer portfolioId,
        @Param("currency") String currency,
        @Param("asOfDate") LocalDate asOfDate
    );
    
    /**
     * Get currency exposures summary.
     */
    @Query("""
        SELECT p.product.currency as currency,
               SUM(p.marketValue) as marketValue,
               SUM(p.quantity) as totalQuantity,
               COUNT(p) as positionCount
        FROM Position p
        JOIN p.snapshot s
        WHERE s.portfolioId = :portfolioId
        AND s.snapshotDate = :asOfDate
        AND p.quantity <> 0
        GROUP BY p.product.currency
        ORDER BY SUM(ABS(p.marketValue)) DESC
    """)
    List<Object[]> findCurrencyExposureSummary(
        @Param("portfolioId") Integer portfolioId,
        @Param("asOfDate") LocalDate asOfDate
    );
}
