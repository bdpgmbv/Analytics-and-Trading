package com.vyshali.positionloader.repository;

/*
 * 12/02/2025 - 1:37 PM
 * @author Vyshali Prabananth Lal
 */

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AuditRepository {
    private final JdbcTemplate jdbcTemplate;

    public void logEvent(String type, String entityId, String actor, String payload) {
        jdbcTemplate.update(AuditSql.INSERT_AUDIT, type, entityId, actor, payload);
    }
}