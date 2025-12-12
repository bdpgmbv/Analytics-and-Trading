package com.vyshali.common.repository;

import com.vyshali.fxanalyzer.common.entity.ShareClass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShareClassRepository extends JpaRepository<ShareClass, Long> {

    List<ShareClass> findByFundFundId(Long fundId);
    
    Optional<ShareClass> findByFundFundIdAndShareClassCode(Long fundId, String shareClassCode);
    
    List<ShareClass> findByCurrency(String currency);
    
    @Query("SELECT s FROM ShareClass s WHERE s.fund.fundId = :fundId AND s.status = 'ACTIVE'")
    List<ShareClass> findActiveByFundId(@Param("fundId") Long fundId);
    
    @Query("SELECT s FROM ShareClass s JOIN FETCH s.fund WHERE s.shareClassId = :shareClassId")
    Optional<ShareClass> findByIdWithFund(@Param("shareClassId") Long shareClassId);
    
    @Query("SELECT DISTINCT s.currency FROM ShareClass s WHERE s.status = 'ACTIVE'")
    List<String> findDistinctCurrencies();
}
