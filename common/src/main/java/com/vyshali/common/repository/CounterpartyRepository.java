package com.vyshali.common.repository;

import com.vyshali.fxanalyzer.common.entity.Counterparty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CounterpartyRepository extends JpaRepository<Counterparty, Long> {

    Optional<Counterparty> findByCounterpartyCode(String counterpartyCode);
    
    @Query("SELECT c FROM Counterparty c WHERE c.isActive = true ORDER BY c.counterpartyName")
    List<Counterparty> findAllActive();
    
    boolean existsByCounterpartyCode(String counterpartyCode);
}
