package com.vyshali.common.repository;

import com.vyshali.fxanalyzer.common.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByAccountNumber(String accountNumber);
    
    List<Account> findByFundFundId(Long fundId);
    
    List<Account> findByAccountType(String accountType);
    
    List<Account> findBySourceSystem(String sourceSystem);
    
    @Query("SELECT a FROM Account a WHERE a.fund.fundId = :fundId AND a.status = 'ACTIVE'")
    List<Account> findActiveByFundId(@Param("fundId") Long fundId);
    
    @Query("SELECT a FROM Account a WHERE a.fund.client.clientId = :clientId AND a.status = 'ACTIVE'")
    List<Account> findActiveByClientId(@Param("clientId") Long clientId);
    
    @Query("SELECT a FROM Account a JOIN FETCH a.fund f JOIN FETCH f.client WHERE a.accountNumber = :accountNumber")
    Optional<Account> findByAccountNumberWithHierarchy(@Param("accountNumber") String accountNumber);
    
    @Query("SELECT a FROM Account a WHERE a.sourceSystem = :sourceSystem AND a.status = 'ACTIVE'")
    List<Account> findActiveBySourceSystem(@Param("sourceSystem") String sourceSystem);
    
    boolean existsByAccountNumber(String accountNumber);
}
