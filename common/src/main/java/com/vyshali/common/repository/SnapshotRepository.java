package com.vyshali.common.repository;

import com.vyshali.fxanalyzer.common.entity.Snapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SnapshotRepository extends JpaRepository<Snapshot, Long> {

    @Query("SELECT s FROM Snapshot s WHERE s.account.accountId = :accountId AND s.snapshotType = :snapshotType " +
           "AND s.snapshotDate = :snapshotDate AND s.status = 'ACTIVE'")
    Optional<Snapshot> findActiveSnapshot(
            @Param("accountId") Long accountId,
            @Param("snapshotType") String snapshotType,
            @Param("snapshotDate") LocalDate snapshotDate);

    List<Snapshot> findBySnapshotDateAndStatus(LocalDate snapshotDate, String status);
    
    List<Snapshot> findByAccountAccountIdAndStatus(Long accountId, String status);
    
    @Query("SELECT s FROM Snapshot s WHERE s.snapshotDate = :date AND s.snapshotType = :type AND s.status = 'ACTIVE'")
    List<Snapshot> findActiveByDateAndType(@Param("date") LocalDate date, @Param("type") String type);
    
    @Query("SELECT s FROM Snapshot s LEFT JOIN FETCH s.positions WHERE s.snapshotId = :snapshotId")
    Optional<Snapshot> findByIdWithPositions(@Param("snapshotId") Long snapshotId);
    
    @Query("SELECT s FROM Snapshot s WHERE s.account.accountId = :accountId " +
           "ORDER BY s.snapshotDate DESC, s.snapshotTime DESC")
    List<Snapshot> findLatestByAccountId(@Param("accountId") Long accountId);
    
    @Modifying
    @Query("UPDATE Snapshot s SET s.status = 'SUPERSEDED' WHERE s.account.accountId = :accountId " +
           "AND s.snapshotType = :snapshotType AND s.snapshotDate = :snapshotDate AND s.status = 'ACTIVE'")
    int supersedePreviousSnapshots(
            @Param("accountId") Long accountId,
            @Param("snapshotType") String snapshotType,
            @Param("snapshotDate") LocalDate snapshotDate);

    @Modifying
    @Query("DELETE FROM Snapshot s WHERE s.snapshotDate < :cutoffDate")
    int deleteOldSnapshots(@Param("cutoffDate") LocalDate cutoffDate);
    
    @Query("SELECT COUNT(s) FROM Snapshot s WHERE s.snapshotDate = :date AND s.status = 'ACTIVE'")
    long countActiveByDate(@Param("date") LocalDate date);
}
