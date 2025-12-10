package com.vyshali.positionloader.controller;

/*
 * 12/10/2025 - 1:43 PM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.positionloader.dto.AccountSnapshotDTO;
import com.vyshali.positionloader.dto.PositionDTO;
import com.vyshali.positionloader.service.SnapshotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Position upload API for FXAN UI.
 * Handles manual position uploads for non-FS accounts.
 */
@Slf4j
@RestController
@RequestMapping("/api/positions")
@RequiredArgsConstructor
public class UploadController {

    private final SnapshotService snapshotService;

    /**
     * Upload positions via CSV file.
     * <p>
     * CSV format (with header):
     * product_id,ticker,asset_class,quantity,price,currency
     */
    @PostMapping("/upload")
    public ResponseEntity<UploadResponse> uploadCsv(@RequestParam("file") MultipartFile file, @RequestParam("accountId") Integer accountId, @RequestParam(value = "clientId", defaultValue = "100") Integer clientId, Authentication auth) {

        String user = auth != null ? auth.getName() : "ANONYMOUS";
        log.info("Upload started: account={}, file={}, user={}", accountId, file.getOriginalFilename(), user);

        try {
            List<PositionDTO> positions = parseCsv(file);

            if (positions.isEmpty()) {
                return ResponseEntity.badRequest().body(new UploadResponse(0, 0, List.of("No valid positions found")));
            }

            AccountSnapshotDTO snapshot = new AccountSnapshotDTO(accountId, clientId, "Uploaded", clientId, "Uploaded Fund", "USD", "ACC-" + accountId, "MANUAL", positions);

            snapshotService.processManualUpload(snapshot, user);

            return ResponseEntity.ok(new UploadResponse(positions.size(), 0, List.of()));

        } catch (Exception e) {
            log.error("Upload failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(new UploadResponse(0, 0, List.of(e.getMessage())));
        }
    }

    /**
     * Upload positions via JSON payload.
     */
    @PostMapping("/upload/json")
    public ResponseEntity<UploadResponse> uploadJson(@Valid @RequestBody AccountSnapshotDTO snapshot, Authentication auth) {

        String user = auth != null ? auth.getName() : "ANONYMOUS";
        log.info("JSON upload: account={}, user={}", snapshot.accountId(), user);

        try {
            snapshotService.processManualUpload(snapshot, user);
            return ResponseEntity.ok(new UploadResponse(snapshot.positionCount(), 0, List.of()));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new UploadResponse(0, 0, List.of(e.getMessage())));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new UploadResponse(0, 0, List.of(e.getMessage())));
        }
    }

    /**
     * Validate positions without saving.
     */
    @PostMapping("/validate")
    public ResponseEntity<Map<String, Object>> validate(@RequestBody AccountSnapshotDTO snapshot) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (snapshot.accountId() == null) {
            errors.add("Account ID is required");
        }

        if (snapshot.positions() != null) {
            long zeroPriceCount = snapshot.positions().stream().filter(PositionDTO::hasZeroPrice).count();
            if (zeroPriceCount > 0) {
                warnings.add(zeroPriceCount + " positions have zero price");
            }
        }

        return ResponseEntity.ok(Map.of("valid", errors.isEmpty(), "errors", errors, "warnings", warnings));
    }

    // ==================== CSV PARSING ====================

    private List<PositionDTO> parseCsv(MultipartFile file) throws Exception {
        List<PositionDTO> positions = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String header = reader.readLine(); // Skip header
            if (header == null) return positions;

            String line;
            int lineNum = 1;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                line = line.trim();
                if (line.isEmpty()) continue;

                try {
                    PositionDTO pos = parseCsvLine(line);
                    if (pos != null) positions.add(pos);
                } catch (Exception e) {
                    log.warn("Skipping invalid CSV line {}: {}", lineNum, e.getMessage());
                }
            }
        }

        return positions;
    }

    private PositionDTO parseCsvLine(String line) {
        String[] parts = line.split(",");
        if (parts.length < 4) return null;

        return new PositionDTO(Integer.parseInt(parts[0].trim()),          // product_id
                parts.length > 1 ? parts[1].trim() : null,  // ticker
                parts.length > 2 ? parts[2].trim() : "EQUITY", // asset_class
                parts.length > 5 ? parts[5].trim() : "USD", // currency
                new BigDecimal(parts[3].trim()),            // quantity
                parts.length > 4 ? new BigDecimal(parts[4].trim()) : BigDecimal.ZERO, // price
                "MANUAL",                                    // txnType
                null                                         // externalRefId
        );
    }

    public record UploadResponse(int uploaded, int failed, List<String> errors) {
    }
}