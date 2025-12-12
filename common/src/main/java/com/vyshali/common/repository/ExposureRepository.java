package com.vyshali.common.repository;

import com.vyshali.fxanalyzer.common.entity.Exposure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExposureRepository extends JpaRepository<Exposure, Long> {

    List<Exposure> findByPositionPositionId(Long positionId);
    
    List<Exposure> findByExposureType(String exposureType);
    
    @Query("SELECT e FROM Exposure e WHERE e.position.positionId = :positionId AND e.exposureType = :exposureType")
    List<Exposure> findByPositionAndType(
            @Param("positionId") Long positionId,
            @Param("exposureType") String exposureType);
    
    @Query("SELECT e FROM Exposure e WHERE e.position.snapshot.snapshotId = :snapshotId")
    List<Exposure> findBySnapshotId(@Param("snapshotId") Long snapshotId);
    
    @Query("SELECT e.currency, SUM(e.exposureAmountBase), e.exposureType FROM Exposure e " +
           "WHERE e.position.snapshot.snapshotId = :snapshotId " +
           "GROUP BY e.currency, e.exposureType")
    List<Object[]> sumExposuresByCurrencyAndType(@Param("snapshotId") Long snapshotId);
    
    @Query("SELECT e FROM Exposure e WHERE e.position.snapshot.snapshotId = :snapshotId " +
           "AND e.currency = :currency AND e.exposureType = :exposureType")
    List<Exposure> findBySnapshotCurrencyAndType(
            @Param("snapshotId") Long snapshotId,
            @Param("currency") String currency,
            @Param("exposureType") String exposureType);
}
