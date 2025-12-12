package com.vyshali.common.repository;

import com.vyshali.fxanalyzer.common.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {

    Optional<Client> findByClientCode(String clientCode);
    
    List<Client> findByStatus(String status);
    
    @Query("SELECT c FROM Client c WHERE c.status = 'ACTIVE' ORDER BY c.clientName")
    List<Client> findAllActive();
    
    @Query("SELECT c FROM Client c LEFT JOIN FETCH c.funds WHERE c.clientId = :clientId")
    Optional<Client> findByIdWithFunds(@Param("clientId") Long clientId);
    
    boolean existsByClientCode(String clientCode);
}
