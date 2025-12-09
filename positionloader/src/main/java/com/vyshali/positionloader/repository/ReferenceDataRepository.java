package com.vyshali.positionloader.repository;

/*
 * 12/1/25 - 22:58
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

    public void ensureClientExists(Integer clientId, String clientName) {
        jdbcTemplate.update(ReferenceDataSql.UPSERT_CLIENT, clientId, clientName);
    }

    public void ensureFundExists(Integer fundId, Integer clientId, String fundName, String currency) {
        jdbcTemplate.update(ReferenceDataSql.UPSERT_FUND, fundId, clientId, fundName, currency);
    }

    public void upsertAccount(Integer accountId, Integer fundId, String accountNum, String type) {
        jdbcTemplate.update(ReferenceDataSql.UPSERT_ACCOUNT, accountId, fundId, accountNum, type);
    }

    public void batchUpsertProducts(List<PositionDetailDTO> positions) {
        jdbcTemplate.batchUpdate(ReferenceDataSql.UPSERT_PRODUCT, new BatchPreparedStatementSetter() {
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                PositionDetailDTO p = positions.get(i);
                ps.setInt(1, p.productId());
                ps.setString(2, p.ticker());
                ps.setString(3, "EQUITY");
                ps.setString(4, "Imported");
            }

            public int getBatchSize() {
                return positions.size();
            }
        });
    }
}