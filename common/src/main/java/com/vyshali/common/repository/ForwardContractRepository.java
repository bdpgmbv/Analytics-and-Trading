package com.vyshali.common.repository;

import com.vyshali.fxanalyzer.common.entity.ForwardContract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ForwardContractRepository extends JpaRepository<ForwardContract, Long> {

    List<ForwardContract> findByAccountAccountId(Long accountId);
    
    List<ForwardContract> findByStatus(String status);
    
    /**
     * Find active forwards for an account
     */
    @Query("SELECT f FROM ForwardContract f WHERE f.account.accountId = :accountId AND f.status = 'ACTIVE'")
    List<ForwardContract> findActiveByAccountId(@Param("accountId") Long accountId);
    
    /**
     * Find active forwards by account number
     */
    @Query("SELECT f FROM ForwardContract f WHERE f.account.accountNumber = :accountNumber AND f.status = 'ACTIVE'")
    List<ForwardContract> findActiveByAccountNumber(@Param("accountNumber") String accountNumber);
    
    /**
     * Find forwards maturing within X days (for Tab 5 - Forward Maturity Alert)
     */
    @Query("SELECT f FROM ForwardContract f WHERE f.status = 'ACTIVE' AND f.valueDate BETWEEN :today AND :futureDate")
    List<ForwardContract> findMaturingWithinDays(
            @Param("today") LocalDate today,
            @Param("futureDate") LocalDate futureDate);
    
    /**
     * Find overdue forwards (maturity date has passed)
     */
    @Query("SELECT f FROM ForwardContract f WHERE f.status = 'ACTIVE' AND f.valueDate < :today")
    List<ForwardContract> findOverdueForwards(@Param("today") LocalDate today);
    
    /**
     * Find forwards by currency pair
     */
    @Query("SELECT f FROM ForwardContract f WHERE f.buyCurrency = :buyCcy AND f.sellCurrency = :sellCcy AND f.status = 'ACTIVE'")
    List<ForwardContract> findByCurrencyPair(
            @Param("buyCcy") String buyCurrency,
            @Param("sellCcy") String sellCurrency);
    
    /**
     * Find forwards maturing on specific date
     */
    List<ForwardContract> findByValueDateAndStatus(LocalDate valueDate, String status);
    
    /**
     * Sum of notional by currency for active forwards
     */
    @Query("SELECT f.buyCurrency, SUM(f.buyAmount), f.sellCurrency, SUM(f.sellAmount) " +
           "FROM ForwardContract f WHERE f.account.accountId = :accountId AND f.status = 'ACTIVE' " +
           "GROUP BY f.buyCurrency, f.sellCurrency")
    List<Object[]> sumNotionalByCurrency(@Param("accountId") Long accountId);
}
