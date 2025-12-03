package com.vyshali.positionloader.service;

/*
 * 12/03/2025 - 1:50 PM
 * @author Vyshali Prabananth Lal
 */

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExposureEnrichmentService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Logic:
     * 1. Delete old exposures for this account.
     * 2. Insert GENERIC exposure (Natural Risk).
     * 3. Insert SPECIFIC exposure (Equity Swap Funding Risk).
     */
    @Transactional
    public void enrichSnapshot(Integer accountId) {
        log.info("Calculating Risk Exposures for Account {}", accountId);

        // 1. Clean Slate
        String deleteSql = """
                    DELETE FROM Position_Exposures 
                    WHERE position_id IN (SELECT position_id FROM Positions WHERE account_id = ?)
                """;
        jdbcTemplate.update(deleteSql, accountId);

        // 2. Generic Exposure: 100% of the Issue Currency
        String genericSql = """
                    INSERT INTO Position_Exposures (position_id, exposure_type, currency, weight)
                    SELECT position_id, 'GENERIC', p.issue_currency, 100.00
                    FROM Positions pos
                    JOIN Products p ON pos.product_id = p.product_id
                    WHERE pos.account_id = ?
                """;
        jdbcTemplate.update(genericSql, accountId);

        // 3. Specific Exposure: Equity Swaps have funding risk in Settlement Ccy
        // Rule: If Asset Class is EQUITY_SWAP, adding -100% exposure to Settlement Ccy
        String specificSql = """
                    INSERT INTO Position_Exposures (position_id, exposure_type, currency, weight)
                    SELECT pos.position_id, 'SPECIFIC_1', p.settlement_currency, -100.00
                    FROM Positions pos
                    JOIN Products p ON pos.product_id = p.product_id
                    WHERE pos.account_id = ? 
                      AND p.asset_class = 'EQUITY_SWAP'
                """;
        jdbcTemplate.update(specificSql, accountId);

        log.info("Exposure Enrichment complete for Account {}", accountId);
    }
}