package com.vyshali.positionloader.repository;

/*
 * 12/09/2025 - Added Idempotency Check
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.positionloader.dto.PositionDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Repository for transaction records and idempotency checks.
 */
@Repository
@RequiredArgsConstructor
public class TransactionRepository {

    private final JdbcTemplate jdbc;
    private final AtomicLong txnSequence = new AtomicLong(System.currentTimeMillis());

    /**
     * Insert transactions for EOD positions.
     */
    public void insertTransactions(Integer accountId, List<PositionDTO> positions) {
        for (PositionDTO p : positions) {
            long txnId = txnSequence.incrementAndGet();
            BigDecimal price = p.price() != null ? p.price() : BigDecimal.ZERO;
            BigDecimal cost = p.quantity().multiply(price);
            String refId = p.externalRefId() != null ? p.externalRefId() : "EOD-" + accountId + "-" + p.productId() + "-" + LocalDate.now();

            jdbc.update("""
                    INSERT INTO Transactions (transaction_id, account_id, product_id, txn_type, 
                                              trade_date, quantity, price, cost_local, external_ref_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (external_ref_id) DO NOTHING
                    """, txnId, accountId, p.productId(), p.txnType() != null ? p.txnType() : "EOD_LOAD", java.sql.Date.valueOf(LocalDate.now()), p.quantity(), price, cost, refId);
        }
    }

    /**
     * Check if transaction already processed (idempotency).
     */
    public boolean exists(String transactionId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(1) FROM Transactions WHERE external_ref_id = ?", Integer.class, transactionId);
        return count != null && count > 0;
    }

    /**
     * Get original quantity for AMEND operations.
     */
    public BigDecimal getQuantityByRefId(String refId) {
        try {
            return jdbc.queryForObject("SELECT quantity FROM Transactions WHERE external_ref_id = ?", BigDecimal.class, refId);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}