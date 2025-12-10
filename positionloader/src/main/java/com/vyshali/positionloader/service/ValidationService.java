package com.vyshali.positionloader.service;

/*
 * 12/10/2025 - 12:57 PM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.positionloader.config.FeatureFlags;
import com.vyshali.positionloader.dto.AccountSnapshotDTO;
import com.vyshali.positionloader.dto.PositionDTO;
import com.vyshali.positionloader.dto.ValidationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Validates position data before saving.
 * Critical: Detects zero prices (Issue #1).
 */
@Slf4j
@Service
public class ValidationService {

    private final FeatureFlags flags;

    public ValidationService(FeatureFlags flags) {
        this.flags = flags;
    }

    /**
     * Validate entire snapshot.
     */
    public ValidationResult validate(AccountSnapshotDTO snapshot) {
        ValidationResult result = new ValidationResult(snapshot.accountId());

        // Account-level checks
        if (snapshot.accountId() == null || snapshot.accountId() <= 0) {
            result.addError("INVALID_ACCOUNT", "Account ID is null or invalid");
        }

        if (!snapshot.isAvailable()) {
            result.addError("UNAVAILABLE", "Snapshot status: " + snapshot.status());
        }

        // Position-level checks
        if (snapshot.positions() == null || snapshot.positions().isEmpty()) {
            result.addWarning("EMPTY", "No positions in snapshot");
        } else {
            validatePositions(snapshot.positions(), result);
        }

        if (result.hasErrors()) {
            log.warn("Validation failed for account {}: {}", snapshot.accountId(), result.errorSummary());
        }

        return result;
    }

    private void validatePositions(List<PositionDTO> positions, ValidationResult result) {
        int zeroPriceCount = 0;

        for (int i = 0; i < positions.size(); i++) {
            PositionDTO p = positions.get(i);
            String prefix = "[" + i + "] ";

            if (p.productId() == null) {
                result.addError("NULL_PRODUCT", prefix + "productId is null");
            }

            if (p.quantity() == null) {
                result.addError("NULL_QTY", prefix + "quantity is null");
            }

            // CRITICAL: Zero price detection
            if (p.hasZeroPrice()) {
                zeroPriceCount++;
                result.addError("ZERO_PRICE", prefix + p.ticker() + " has zero price");
            }

            if (p.ticker() == null || p.ticker().isBlank()) {
                result.addWarning("NO_TICKER", prefix + "ticker missing");
            }
        }

        // Check threshold
        double zeroPct = (double) zeroPriceCount / positions.size() * 100;
        if (zeroPct > flags.getValidation().getZeroPriceThresholdPct()) {
            result.addError("PRICE_SERVICE_DOWN", String.format("%.1f%% positions have zero price - Price Service may be DOWN!", zeroPct));
        }
    }

    /**
     * Quick validation for single position.
     */
    public boolean isValid(PositionDTO p) {
        return p != null && p.productId() != null && p.quantity() != null && !p.hasZeroPrice();
    }
}
