package com.vyshali.common.repository;

import com.vyshali.fxanalyzer.common.entity.CashBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CashBalanceRepository extends JpaRepository<CashBalance, Long> {

    List<CashBalance> findByAccountAccountId(Long accountId);
    
    List<CashBalance> findByBalanceDate(LocalDate balanceDate);
    
    Optional<CashBalance> findByAccountAccountIdAndCurrencyAndBalanceDate(
            Long accountId, String currency, LocalDate balanceDate);
    
    @Query("SELECT c FROM CashBalance c WHERE c.account.accountNumber = :accountNumber AND c.balanceDate = :balanceDate")
    List<CashBalance> findByAccountNumberAndDate(
            @Param("accountNumber") String accountNumber,
            @Param("balanceDate") LocalDate balanceDate);
    
    @Query("SELECT c FROM CashBalance c WHERE c.account.accountId = :accountId " +
           "AND c.balanceDate = (SELECT MAX(c2.balanceDate) FROM CashBalance c2 WHERE c2.account.accountId = :accountId)")
    List<CashBalance> findLatestByAccountId(@Param("accountId") Long accountId);
    
    @Query("SELECT c.currency, SUM(c.cashBalance) FROM CashBalance c " +
           "WHERE c.account.accountId = :accountId AND c.balanceDate = :balanceDate " +
           "GROUP BY c.currency")
    List<Object[]> sumBalancesByCurrency(
            @Param("accountId") Long accountId,
            @Param("balanceDate") LocalDate balanceDate);
    
    @Query("SELECT c FROM CashBalance c WHERE c.unhedgedExposure IS NOT NULL AND c.unhedgedExposure <> 0 " +
           "AND c.balanceDate = :balanceDate")
    List<CashBalance> findWithUnhedgedExposure(@Param("balanceDate") LocalDate balanceDate);
}
