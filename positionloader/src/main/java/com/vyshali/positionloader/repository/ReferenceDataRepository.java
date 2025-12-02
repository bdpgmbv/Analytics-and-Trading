package com.vyshali.positionloader.repository;

/*
 * 12/1/25 - 22:58
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.positionloader.dto.PositionDetailDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

@Repository
@RequiredArgsConstructor
public class ReferenceDataRepository {

    private final JdbcTemplate jdbcTemplate;

    @Cacheable(value = "clients", key = "#id")
    public boolean ensureClientExists(Integer id, String name) {
        String sql = "INSERT INTO Clients (client_id, client_name) VALUES (?, ?) ON CONFLICT (client_id) DO UPDATE SET client_name = EXCLUDED.client_name";
        jdbcTemplate.update(sql, id, name);
        return true;
    }

    @Cacheable(value = "funds", key = "#id")
    public boolean ensureFundExists(Integer id, Integer clientId, String name, String ccy) {
        String sql = "INSERT INTO Funds (fund_id, client_id, fund_name, base_currency) VALUES (?, ?, ?, ?) ON CONFLICT (fund_id) DO UPDATE SET fund_name = EXCLUDED.fund_name, base_currency = EXCLUDED.base_currency";
        jdbcTemplate.update(sql, id, clientId, name, ccy);
        return true;
    }

    public void upsertAccount(Integer id, Integer fundId, String number, String type) {
        String sql = "INSERT INTO Accounts (account_id, fund_id, account_number, account_type) VALUES (?, ?, ?, ?) ON CONFLICT (account_id) DO UPDATE SET account_type = EXCLUDED.account_type, fund_id = EXCLUDED.fund_id";
        jdbcTemplate.update(sql, id, fundId, number, type);
    }

    public void batchUpsertProducts(List<PositionDetailDTO> positions) {
        var uniqueProducts = positions.stream().filter(distinctByKey(PositionDetailDTO::getProductId)).toList();
        if (uniqueProducts.isEmpty()) return;

        String sql = "INSERT INTO Products (product_id, ticker, asset_class, issue_currency) VALUES (?, ?, ?, ?) ON CONFLICT (product_id) DO NOTHING";

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                PositionDetailDTO p = uniqueProducts.get(i);
                ps.setInt(1, p.getProductId());
                ps.setString(2, p.getTicker());
                ps.setString(3, p.getAssetClass());
                ps.setString(4, p.getIssueCurrency());
            }

            @Override
            public int getBatchSize() {
                return uniqueProducts.size();
            }
        });
    }

    private static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
        Map<Object, Boolean> seen = new ConcurrentHashMap<>();
        return t -> seen.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE) == null;
    }
}