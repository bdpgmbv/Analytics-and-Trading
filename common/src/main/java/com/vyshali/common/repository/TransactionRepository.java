package com.vyshali.common.repository;

import com.vyshali.fxanalyzer.common.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByAccountAccountId(Long accountId);
    
    List<Transaction> findByTradeDate(LocalDate tradeDate);
    
    List<Transaction> findByAccountAccountIdAndTradeDate(Long accountId, LocalDate tradeDate);
    
    @Query("SELECT t FROM Transaction t JOIN FETCH t.product WHERE t.account.accountId = :accountId AND t.tradeDate = :tradeDate")
    List<Transaction> findByAccountAndTradeDateWithProduct(
            @Param("accountId") Long accountId,
            @Param("tradeDate") LocalDate tradeDate);
    
    @Query("SELECT t FROM Transaction t JOIN FETCH t.product WHERE t.account.accountNumber = :accountNumber AND t.tradeDate = :tradeDate")
    List<Transaction> findByAccountNumberAndTradeDate(
            @Param("accountNumber") String accountNumber,
            @Param("tradeDate") LocalDate tradeDate);
    
    @Query("SELECT t FROM Transaction t WHERE t.tradeDate = CURRENT_DATE AND t.account.accountId = :accountId")
    List<Transaction> findCurrentDayTransactions(@Param("accountId") Long accountId);
    
    @Query("SELECT t FROM Transaction t WHERE t.source = :source AND t.tradeDate = :tradeDate")
    List<Transaction> findBySourceAndTradeDate(
            @Param("source") String source,
            @Param("tradeDate") LocalDate tradeDate);
    
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.tradeDate = :tradeDate")
    long countByTradeDate(@Param("tradeDate") LocalDate tradeDate);
}
