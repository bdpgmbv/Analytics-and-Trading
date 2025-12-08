package com.vyshali.hedgeservice.service;

/*
 * 12/03/2025 - 12:13 PM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.hedgeservice.dto.*;
import com.vyshali.hedgeservice.repository.HedgeSql;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class HedgeAnalyticsService {

    private final JdbcTemplate jdbcTemplate;

    public List<TransactionViewDTO> getTransactions(Integer accountId) {
        return List.of();
    }

    public List<PositionUploadDTO> getPositionUploadView(Integer accountId) {
        return List.of();
    }

    @Cacheable(value = "hedgePositions", key = "#accountId")
    public List<HedgePositionDTO> getHedgePositions(Integer accountId) {
        log.info("Fetching Hedge Positions from DB for Account {}", accountId);

        return jdbcTemplate.query(HedgeSql.GET_HEDGE_POSITIONS, (rs, rowNum) -> new HedgePositionDTO(rs.getString("identifier_type"), rs.getString("identifier_value"), rs.getString("asset_class"), rs.getString("issue_currency"), rs.getString("gen_exp_ccy"), rs.getBigDecimal("gen_exp_weight"), rs.getString("spec_exp_ccy"), rs.getBigDecimal("spec_exp_weight"), BigDecimal.ONE, BigDecimal.ZERO, rs.getBigDecimal("quantity"), rs.getBigDecimal("market_value_base"), rs.getBigDecimal("cost_local")), accountId);
    }

    public List<ForwardMaturityDTO> getForwardMaturityAlerts(Integer accountId) {
        return List.of();
    }

    public List<CashManagementDTO> getCashManagement(Integer fundId) {
        return List.of();
    }

    public void saveManualPosition(ManualPositionInputDTO input) {
    }
}