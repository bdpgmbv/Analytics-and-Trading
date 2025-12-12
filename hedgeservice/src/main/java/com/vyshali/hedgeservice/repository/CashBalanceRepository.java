package com.vyshali.hedgeservice.repository;

import com.fxanalyzer.hedgeservice.entity.CashBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CashBalanceRepository extends JpaRepository<CashBalance, Long> {
    
    /**
     * Get all cash balances for a portfolio on a specific date.
     */
    @Query("""
        SELECT c FROM CashBalance c
        WHERE c.portfolioId = :portfolioId
        AND c.asOfDate = :asOfDate
        ORDER BY c.currency
    """)
    List<CashBalance> findByPortfolioAndDate(
        @Param("portfolioId") Integer portfolioId,
        @Param("asOfDate") LocalDate asOfDate
    );
    
    /**
     * Get latest cash balances for a portfolio.
     */
    @Query("""
        SELECT c FROM CashBalance c
        WHERE c.portfolioId = :portfolioId
        AND c.asOfDate = (
            SELECT MAX(c2.asOfDate) 
            FROM CashBalance c2 
            WHERE c2.portfolioId = :portfolioId
        )
        ORDER BY c.currency
    """)
    List<CashBalance> findLatestByPortfolio(@Param("portfolioId") Integer portfolioId);
    
    /**
     * Get cash balance for specific currency.
     */
    @Query("""
        SELECT c FROM CashBalance c
        WHERE c.portfolioId = :portfolioId
        AND c.currency = :currency
        AND c.asOfDate = :asOfDate
    """)
    Optional<CashBalance> findByPortfolioCurrencyAndDate(
        @Param("portfolioId") Integer portfolioId,
        @Param("currency") String currency,
        @Param("asOfDate") LocalDate asOfDate
    );
    
    /**
     * Get cash balances by account.
     */
    @Query("""
        SELECT c FROM CashBalance c
        WHERE c.portfolioId = :portfolioId
        AND c.account.accountId = :accountId
        AND c.asOfDate = :asOfDate
        ORDER BY c.currency
    """)
    List<CashBalance> findByPortfolioAccountAndDate(
        @Param("portfolioId") Integer portfolioId,
        @Param("accountId") Integer accountId,
        @Param("asOfDate") LocalDate asOfDate
    );
    
    /**
     * Get overdraft cash balances.
     */
    @Query("""
        SELECT c FROM CashBalance c
        WHERE c.portfolioId = :portfolioId
        AND c.asOfDate = :asOfDate
        AND c.balance < 0
        ORDER BY c.balance ASC
    """)
    List<CashBalance> findOverdrafts(
        @Param("portfolioId") Integer portfolioId,
        @Param("asOfDate") LocalDate asOfDate
    );
    
    /**
     * Get total cash in base currency.
     */
    @Query("""
        SELECT SUM(c.balanceBase)
        FROM CashBalance c
        WHERE c.portfolioId = :portfolioId
        AND c.asOfDate = :asOfDate
    """)
    Optional<java.math.BigDecimal> getTotalCashBase(
        @Param("portfolioId") Integer portfolioId,
        @Param("asOfDate") LocalDate asOfDate
    );
}
