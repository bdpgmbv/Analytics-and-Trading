-- liquibase formatted sql
-- changeset admin:5

-- OPTIMIZATION: Materialized View for Cash Management
-- Aggregates millions of positions into a snapshot for the UI.
CREATE MATERIALIZED VIEW mv_cash_exposure AS
SELECT
    p.issue_currency,
    a.fund_id,
    SUM(p.quantity) as cash_balance,
    SUM(p.market_value_base) as unhedged_exposure,
    COUNT(*) as position_count
FROM Positions p
JOIN Products pr ON p.product_id = pr.product_id
JOIN Accounts a ON p.account_id = a.account_id
WHERE pr.asset_class = 'CASH'
GROUP BY p.issue_currency, a.fund_id;

-- Index for lightning-fast dashboard loading
CREATE INDEX idx_mv_cash_fund ON mv_cash_exposure(fund_id);

-- Helper function to refresh the view (Call via Scheduled Job)
CREATE OR REPLACE FUNCTION refresh_cash_mv()
RETURNS void AS $$
BEGIN
    REFRESH MATERIALIZED VIEW CONCURRENTLY mv_cash_exposure;
END;
$$ LANGUAGE plpgsql;