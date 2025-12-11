package com.vyshali.positionloader.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Repository for position archival operations (Phase 4 #22).
 */
@Repository
public class ArchivalRepository {
    
    private static final Logger log = LoggerFactory.getLogger(ArchivalRepository.class);
    
    private final JdbcTemplate jdbcTemplate;
    
    public ArchivalRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    /**
     * Archive positions older than specified date.
     * Moves from Account_Positions to Account_Positions_Archive.
     */
    @Transactional
    public int archivePositions(LocalDate olderThan) {
        log.info("Archiving positions older than {}", olderThan);
        
        // Insert into archive table
        String insertSql = """
            INSERT INTO account_positions_archive 
            SELECT * FROM account_positions 
            WHERE business_date < ? 
            AND position_id NOT IN (SELECT position_id FROM account_positions_archive)
            """;
        int inserted = jdbcTemplate.update(insertSql, olderThan);
        
        // Delete from main table
        String deleteSql = """
            DELETE FROM account_positions 
            WHERE business_date < ? 
            AND position_id IN (SELECT position_id FROM account_positions_archive)
            """;
        int deleted = jdbcTemplate.update(deleteSql, olderThan);
        
        log.info("Archived {} positions (inserted: {}, deleted: {})", deleted, inserted, deleted);
        return deleted;
    }
    
    /**
     * Archive positions for specific account.
     */
    @Transactional
    public int archiveAccountPositions(int accountId, LocalDate olderThan) {
        log.info("Archiving positions for account {} older than {}", accountId, olderThan);
        
        String insertSql = """
            INSERT INTO account_positions_archive 
            SELECT * FROM account_positions 
            WHERE account_id = ? AND business_date < ?
            AND position_id NOT IN (SELECT position_id FROM account_positions_archive)
            """;
        int inserted = jdbcTemplate.update(insertSql, accountId, olderThan);
        
        String deleteSql = """
            DELETE FROM account_positions 
            WHERE account_id = ? AND business_date < ?
            AND position_id IN (SELECT position_id FROM account_positions_archive)
            """;
        int deleted = jdbcTemplate.update(deleteSql, accountId, olderThan);
        
        return deleted;
    }
    
    /**
     * Get count of archived positions.
     */
    public long getArchiveCount() {
        String sql = "SELECT COUNT(*) FROM account_positions_archive";
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null ? count : 0;
    }
    
    /**
     * Get archive count for account.
     */
    public long getArchiveCount(int accountId) {
        String sql = "SELECT COUNT(*) FROM account_positions_archive WHERE account_id = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, accountId);
        return count != null ? count : 0;
    }
    
    /**
     * Restore positions from archive.
     */
    @Transactional
    public int restorePositions(int accountId, LocalDate businessDate) {
        log.info("Restoring archived positions for account {} date {}", accountId, businessDate);
        
        String insertSql = """
            INSERT INTO account_positions 
            SELECT * FROM account_positions_archive 
            WHERE account_id = ? AND business_date = ?
            AND position_id NOT IN (SELECT position_id FROM account_positions)
            """;
        int restored = jdbcTemplate.update(insertSql, accountId, businessDate);
        
        String deleteSql = """
            DELETE FROM account_positions_archive 
            WHERE account_id = ? AND business_date = ?
            AND position_id IN (SELECT position_id FROM account_positions)
            """;
        jdbcTemplate.update(deleteSql, accountId, businessDate);
        
        return restored;
    }
    
    /**
     * Purge old archived data.
     */
    @Transactional
    public int purgeArchive(LocalDate olderThan) {
        log.info("Purging archive older than {}", olderThan);
        String sql = "DELETE FROM account_positions_archive WHERE business_date < ?";
        return jdbcTemplate.update(sql, olderThan);
    }
    
    /**
     * Get archive date range.
     */
    public ArchiveDateRange getArchiveDateRange() {
        String sql = """
            SELECT MIN(business_date) as min_date, MAX(business_date) as max_date 
            FROM account_positions_archive
            """;
        return jdbcTemplate.query(sql, rs -> {
            if (rs.next()) {
                LocalDate minDate = rs.getObject("min_date", LocalDate.class);
                LocalDate maxDate = rs.getObject("max_date", LocalDate.class);
                return new ArchiveDateRange(minDate, maxDate);
            }
            return new ArchiveDateRange(null, null);
        });
    }
    
    /**
     * Archive date range record.
     */
    public record ArchiveDateRange(LocalDate minDate, LocalDate maxDate) {
        public boolean isEmpty() {
            return minDate == null || maxDate == null;
        }
    }
}
