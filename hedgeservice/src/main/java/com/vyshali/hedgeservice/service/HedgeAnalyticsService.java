package com.vyshali.hedgeservice.service;

/*
 * 12/03/2025 - 12:13 PM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.hedgeservice.dto.*;
import com.vyshali.hedgeservice.repository.HedgeRepository;
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
    private final HedgeRepository hedgeRepository; // <--- INJECTED NEW REPO

    // Existing Stubbed Methods
    public List<TransactionViewDTO> getTransactions(Integer accountId) {
        return List.of(); // Still stubbed for now (low priority)
    }

    public List<PositionUploadDTO> getPositionUploadView(Integer accountId) {
        return List.of(); // Still stubbed
    }

    @Cacheable(value = "hedgePositions", key = "#accountId")
    public List<HedgePositionDTO> getHedgePositions(Integer accountId) {
        // Keep existing working implementation
        return jdbcTemplate.query(HedgeSql.GET_HEDGE_POSITIONS, (rs, rowNum) -> new HedgePositionDTO(rs.getString("identifier_type"), rs.getString("identifier_value"), rs.getString("asset_class"), rs.getString("issue_currency"), rs.getString("gen_exp_ccy"), rs.getBigDecimal("gen_exp_weight"), rs.getString("spec_exp_ccy"), rs.getBigDecimal("spec_exp_weight"), BigDecimal.ONE, BigDecimal.ZERO, rs.getBigDecimal("quantity"), rs.getBigDecimal("market_value_base"), rs.getBigDecimal("cost_local")), accountId);
    }

    // --- IMPLEMENTED LOGIC ---

    public List<ForwardMaturityDTO> getForwardMaturityAlerts(Integer accountId) {
        log.info("Calculating Forward Alerts for Account {}", accountId);
        return hedgeRepository.findForwardMaturityAlerts(accountId);
    }

    public List<CashManagementDTO> getCashManagement(Integer fundId) {
        log.info("Aggregating Cash Balances for Fund {}", fundId);
        return hedgeRepository.findCashBalances(fundId);
    }

    public void saveManualPosition(ManualPositionInputDTO input) {
        // Implementation for Manual Upload (INSERT into Transactions with source='MANUAL')
        // Left as exercise for brevity
    }
}