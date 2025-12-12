package com.vyshali.common.repository;

import com.vyshali.fxanalyzer.common.entity.Position;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PositionRepository extends JpaRepository<Position, Long> {

    List<Position> findBySnapshotSnapshotId(Long snapshotId);
    
    List<Position> findByAccountAccountId(Long accountId);
    
    @Query("SELECT p FROM Position p JOIN FETCH p.product WHERE p.snapshot.snapshotId = :snapshotId")
    List<Position> findBySnapshotIdWithProduct(@Param("snapshotId") Long snapshotId);
    
    @Query("SELECT p FROM Position p JOIN FETCH p.product LEFT JOIN FETCH p.exposures " +
           "WHERE p.snapshot.snapshotId = :snapshotId")
    List<Position> findBySnapshotIdWithProductAndExposures(@Param("snapshotId") Long snapshotId);
    
    @Query("SELECT p FROM Position p JOIN p.snapshot s " +
           "WHERE s.account.accountId = :accountId AND s.snapshotDate = :date AND s.status = 'ACTIVE'")
    List<Position> findActivePositionsByAccountAndDate(
            @Param("accountId") Long accountId,
            @Param("date") LocalDate date);
    
    @Query("SELECT p FROM Position p JOIN p.snapshot s JOIN FETCH p.product " +
           "WHERE s.account.accountNumber = :accountNumber AND s.status = 'ACTIVE' " +
           "AND s.snapshotDate = (SELECT MAX(s2.snapshotDate) FROM Snapshot s2 WHERE s2.account.accountNumber = :accountNumber AND s2.status = 'ACTIVE')")
    List<Position> findLatestPositionsByAccountNumber(@Param("accountNumber") String accountNumber);
    
    @Query("SELECT p FROM Position p WHERE p.snapshot.snapshotId = :snapshotId AND p.isExcluded = false")
    List<Position> findIncludedPositions(@Param("snapshotId") Long snapshotId);
    
    @Query("SELECT p FROM Position p WHERE p.snapshot.snapshotId = :snapshotId " +
           "AND p.product.issueCurrency = :currency")
    List<Position> findBySnapshotAndCurrency(
            @Param("snapshotId") Long snapshotId,
            @Param("currency") String currency);
    
    @Query("SELECT SUM(p.marketValueBase) FROM Position p WHERE p.snapshot.snapshotId = :snapshotId AND p.isExcluded = false")
    Optional<BigDecimal> sumMarketValueBase(@Param("snapshotId") Long snapshotId);
    
    @Query("SELECT p.product.issueCurrency, SUM(p.marketValueBase) FROM Position p " +
           "WHERE p.snapshot.snapshotId = :snapshotId AND p.isExcluded = false " +
           "GROUP BY p.product.issueCurrency")
    List<Object[]> sumMarketValueBaseByCurrency(@Param("snapshotId") Long snapshotId);
}
