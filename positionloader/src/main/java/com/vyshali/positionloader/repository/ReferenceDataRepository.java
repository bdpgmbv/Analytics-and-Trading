package com.vyshali.positionloader.repository;

/*
 * 12/1/25 - 22:58
 * FIXED: Updated to use correct SQL with all required columns
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.positionloader.dto.PositionDetailDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ReferenceDataRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Ensure Client exists (upsert)
     */
    public void ensureClientExists(Integer clientId, String clientName) {
        jdbcTemplate.update(ReferenceDataSql.UPSERT_CLIENT, clientId, clientName);
    }

    /**
     * Ensure Fund exists (upsert)
     */
    public void ensureFundExists(Integer fundId, Integer clientId, String fundName, String currency) {
        jdbcTemplate.update(ReferenceDataSql.UPSERT_FUND, fundId, clientId, fundName, currency);
    }

    /**
     * Upsert Account - Using the JOIN-based query
     * <p>
     * Note: This requires Fund to exist first (which it should after ensureFundExists)
     */
    public void upsertAccount(Integer accountId, Integer fundId, String accountNum, String type) {
        // Parameters: accountId, fundId (for insert), accountNum, type, fundId (for SELECT)
        jdbcTemplate.update(ReferenceDataSql.UPSERT_ACCOUNT, accountId, fundId, accountNum, type, fundId);
    }

    /**
     * Alternative: Upsert Account with all values directly from DTO
     */
    public void upsertAccountDirect(Integer accountId, Integer clientId, String clientName, Integer fundId, String fundName, String baseCurrency, String accountNum, String type) {
        jdbcTemplate.update(ReferenceDataSql.UPSERT_ACCOUNT_DIRECT, accountId, clientId, clientName, fundId, fundName, baseCurrency, accountNum, type);
    }

    /**
     * Batch upsert products from position list
     */
    public void batchUpsertProducts(List<PositionDetailDTO> positions) {
        jdbcTemplate.batchUpdate(ReferenceDataSql.UPSERT_PRODUCT, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                PositionDetailDTO p = positions.get(i);
                ps.setInt(1, p.productId());
                ps.setString(2, p.ticker());
                ps.setString(3, p.assetClass() != null ? p.assetClass() : "EQUITY");
                ps.setString(4, "Imported from " + (p.ticker() != null ? p.ticker() : "Unknown"));
                ps.setString(5, p.ticker()); // identifier_value = ticker
            }

            @Override
            public int getBatchSize() {
                return positions.size();
            }
        });
    }
}