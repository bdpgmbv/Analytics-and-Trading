package com.vyshali.hedgeservice.repository;

/*
 * 12/08/2025 - 5:44 PM
 * @author Vyshali Prabananth Lal
 */

public final class HedgeSql {

    private HedgeSql() {
    }

    public static final String GET_HEDGE_POSITIONS = """
                SELECT 
                    p.identifier_type, p.identifier_value, p.asset_class, p.issue_currency,
                    MAX(CASE WHEN pe.exposure_type = 'GENERIC' THEN pe.currency END) as gen_exp_ccy,
                    MAX(CASE WHEN pe.exposure_type = 'GENERIC' THEN pe.weight END) as gen_exp_weight,
                    MAX(CASE WHEN pe.exposure_type = 'SPECIFIC_1' THEN pe.currency END) as spec_exp_ccy,
                    MAX(CASE WHEN pe.exposure_type = 'SPECIFIC_1' THEN pe.weight END) as spec_exp_weight,
                    pos.quantity, pos.market_value_base, pos.cost_local
                FROM Positions pos
                JOIN Account_Batches ab ON pos.account_id = ab.account_id AND pos.batch_id = ab.batch_id
                JOIN Products p ON pos.product_id = p.product_id
                LEFT JOIN Position_Exposures pe ON pos.position_id = pe.position_id
                WHERE pos.account_id = ? 
                  AND ab.status = 'ACTIVE'
                GROUP BY pos.position_id, p.identifier_type, p.identifier_value, p.asset_class, p.issue_currency, pos.quantity, pos.market_value_base, pos.cost_local
            """;
}
