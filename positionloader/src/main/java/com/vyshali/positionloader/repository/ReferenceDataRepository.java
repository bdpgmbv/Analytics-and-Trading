package com.vyshali.positionloader.repository;

/*
 * 12/1/25 - 22:58
 * FIXED: Updated to use correct SQL with all required columns
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.positionloader.dto.AccountSnapshotDTO;
import com.vyshali.positionloader.dto.PositionDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for reference data: Clients, Funds, Accounts, Products.
 */
@Repository
@RequiredArgsConstructor
public class ReferenceDataRepository {

    private final JdbcTemplate jdbc;

    /**
     * Ensure all reference data exists before inserting positions.
     */
    public void ensureReferenceData(AccountSnapshotDTO snapshot) {
        ensureClient(snapshot.clientId(), snapshot.clientName());
        ensureFund(snapshot.fundId(), snapshot.clientId(), snapshot.fundName(), snapshot.baseCurrency());
        ensureAccount(snapshot);

        if (snapshot.positions() != null) {
            ensureProducts(snapshot.positions());
        }
    }

    private void ensureClient(Integer clientId, String clientName) {
        jdbc.update("""
                INSERT INTO Clients (client_id, client_name, status, updated_at) 
                VALUES (?, ?, 'ACTIVE', CURRENT_TIMESTAMP) 
                ON CONFLICT (client_id) DO UPDATE SET 
                    client_name = EXCLUDED.client_name, updated_at = CURRENT_TIMESTAMP
                """, clientId, clientName);
    }

    private void ensureFund(Integer fundId, Integer clientId, String fundName, String currency) {
        jdbc.update("""
                INSERT INTO Funds (fund_id, client_id, fund_name, base_currency, status, updated_at) 
                VALUES (?, ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP) 
                ON CONFLICT (fund_id) DO UPDATE SET 
                    fund_name = EXCLUDED.fund_name, base_currency = EXCLUDED.base_currency, updated_at = CURRENT_TIMESTAMP
                """, fundId, clientId, fundName, currency);
    }

    private void ensureAccount(AccountSnapshotDTO s) {
        jdbc.update("""
                INSERT INTO Accounts (account_id, client_id, client_name, fund_id, fund_name, 
                                      base_currency, account_number, account_type) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (account_id) DO UPDATE SET 
                    account_number = EXCLUDED.account_number, account_type = EXCLUDED.account_type
                """, s.accountId(), s.clientId(), s.clientName(), s.fundId(), s.fundName(), s.baseCurrency(), s.accountNumber(), s.accountType());
    }

    private void ensureProducts(List<PositionDTO> positions) {
        for (PositionDTO p : positions) {
            jdbc.update("""
                    INSERT INTO Products (product_id, ticker, asset_class, description, identifier_type, identifier_value) 
                    VALUES (?, ?, ?, ?, 'TICKER', ?) 
                    ON CONFLICT (product_id) DO UPDATE SET 
                        ticker = EXCLUDED.ticker, identifier_value = EXCLUDED.identifier_value
                    """, p.productId(), p.ticker(), p.assetClass() != null ? p.assetClass() : "EQUITY", "Imported: " + p.ticker(), p.ticker());
        }
    }
}