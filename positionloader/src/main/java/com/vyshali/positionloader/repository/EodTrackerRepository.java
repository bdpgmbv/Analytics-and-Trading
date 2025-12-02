package com.vyshali.positionloader.repository;

/*
 * 12/02/2025 - 11:14 AM
 * @author Vyshali Prabananth Lal
 */

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
@RequiredArgsConstructor
public class EodTrackerRepository {
    private final JdbcTemplate jdbcTemplate;

    public void markAccountComplete(Integer accountId, Integer clientId, LocalDate date) {
        String sql = "INSERT INTO Eod_Daily_Status (account_id, client_id, business_date, status) VALUES (?, ?, ?, 'COMPLETED') ON CONFLICT (account_id, business_date) DO NOTHING";
        jdbcTemplate.update(sql, accountId, clientId, date);
    }

    public boolean isClientFullyComplete(Integer clientId, LocalDate date) {
        String sqlTotal = "SELECT COUNT(*) FROM Accounts WHERE fund_id IN (SELECT fund_id FROM Funds WHERE client_id = ?)";
        Integer total = jdbcTemplate.queryForObject(sqlTotal, Integer.class, clientId);
        String sqlDone = "SELECT COUNT(*) FROM Eod_Daily_Status WHERE client_id = ? AND business_date = ?";
        Integer done = jdbcTemplate.queryForObject(sqlDone, Integer.class, clientId, date);
        return total != null && total.equals(done) && total > 0;
    }
}
