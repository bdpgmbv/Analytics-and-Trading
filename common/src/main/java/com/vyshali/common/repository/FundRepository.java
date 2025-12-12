package com.vyshali.common.repository;

import com.vyshali.fxanalyzer.common.entity.Fund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FundRepository extends JpaRepository<Fund, Long> {

    Optional<Fund> findByFundCode(String fundCode);
    
    List<Fund> findByClientClientId(Long clientId);
    
    List<Fund> findByStatus(String status);
    
    List<Fund> findByBaseCurrency(String baseCurrency);
    
    @Query("SELECT f FROM Fund f WHERE f.client.clientId = :clientId AND f.status = 'ACTIVE'")
    List<Fund> findActiveByClientId(@Param("clientId") Long clientId);
    
    @Query("SELECT f FROM Fund f LEFT JOIN FETCH f.accounts WHERE f.fundId = :fundId")
    Optional<Fund> findByIdWithAccounts(@Param("fundId") Long fundId);
    
    @Query("SELECT f FROM Fund f LEFT JOIN FETCH f.shareClasses WHERE f.fundId = :fundId")
    Optional<Fund> findByIdWithShareClasses(@Param("fundId") Long fundId);
    
    @Query("SELECT DISTINCT f.baseCurrency FROM Fund f WHERE f.status = 'ACTIVE'")
    List<String> findDistinctBaseCurrencies();
    
    boolean existsByFundCode(String fundCode);
}
