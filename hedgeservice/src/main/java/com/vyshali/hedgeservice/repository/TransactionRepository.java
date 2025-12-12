package com.vyshali.hedgeservice.repository;

import com.fxanalyzer.hedgeservice.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    
    /**
     * Get all transactions for a portfolio on a specific date.
     */
    @Query("""
        SELECT t FROM Transaction t
        WHERE t.portfolioId = :portfolioId
        AND t.transactionDate = :transactionDate
        ORDER BY t.createdAt DESC
    """)
    List<Transaction> findByPortfolioAndDate(
        @Param("portfolioId") Integer portfolioId,
        @Param("transactionDate") LocalDate transactionDate
    );
    
    /**
     * Get transactions for current day (intraday transactions).
     */
    @Query("""
        SELECT t FROM Transaction t
        WHERE t.portfolioId = :portfolioId
        AND t.transactionDate = :today
        AND t.status <> 'CANCELLED'
        ORDER BY t.createdAt DESC
    """)
    List<Transaction> findCurrentDayTransactions(
        @Param("portfolioId") Integer portfolioId,
        @Param("today") LocalDate today
    );
    
    /**
     * Get transactions within date range.
     */
    @Query("""
        SELECT t FROM Transaction t
        WHERE t.portfolioId = :portfolioId
        AND t.transactionDate BETWEEN :startDate AND :endDate
        ORDER BY t.transactionDate DESC, t.createdAt DESC
    """)
    List<Transaction> findByPortfolioAndDateRange(
        @Param("portfolioId") Integer portfolioId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );
    
    /**
     * Get transactions by type for a date.
     */
    @Query("""
        SELECT t FROM Transaction t
        WHERE t.portfolioId = :portfolioId
        AND t.transactionDate = :transactionDate
        AND t.transactionType = :transactionType
        ORDER BY t.createdAt DESC
    """)
    List<Transaction> findByPortfolioDateAndType(
        @Param("portfolioId") Integer portfolioId,
        @Param("transactionDate") LocalDate transactionDate,
        @Param("transactionType") String transactionType
    );
    
    /**
     * Get pending transactions.
     */
    @Query("""
        SELECT t FROM Transaction t
        WHERE t.portfolioId = :portfolioId
        AND t.status = 'PENDING'
        ORDER BY t.createdAt DESC
    """)
    List<Transaction> findPendingTransactions(@Param("portfolioId") Integer portfolioId);
    
    /**
     * Count transactions by status for a date.
     */
    @Query("""
        SELECT t.status, COUNT(t)
        FROM Transaction t
        WHERE t.portfolioId = :portfolioId
        AND t.transactionDate = :transactionDate
        GROUP BY t.status
    """)
    List<Object[]> countTransactionsByStatus(
        @Param("portfolioId") Integer portfolioId,
        @Param("transactionDate") LocalDate transactionDate
    );
}
