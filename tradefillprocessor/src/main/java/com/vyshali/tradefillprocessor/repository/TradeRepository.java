package com.vyshali.tradefillprocessor.repository;

/*
 * 12/03/2025 - 1:16 PM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.tradefillprocessor.dto.FillDetailsDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class TradeRepository {
    private final JdbcTemplate jdbcTemplate;

    public List<FillDetailsDTO> getFillsForOrder(String orderId) {
        String sql = """
                    SELECT exec_id, fill_qty, fill_price, fill_time, venue
                    FROM Execution_Fills
                    WHERE order_id = ?
                    ORDER BY fill_time DESC
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new FillDetailsDTO(rs.getString("exec_id"), rs.getBigDecimal("fill_qty"), rs.getBigDecimal("fill_price"), rs.getTimestamp("fill_time").toLocalDateTime(), rs.getString("venue")), orderId);
    }
}
