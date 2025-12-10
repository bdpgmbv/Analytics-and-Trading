package com.vyshali.positionloader.controller;

/*
 * 12/10/2025 - 1:43 PM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.positionloader.dto.AccountSnapshotDTO;
import com.vyshali.positionloader.dto.PositionDTO;
import com.vyshali.positionloader.dto.ValidationResult;
import com.vyshali.positionloader.service.SnapshotService;
import com.vyshali.positionloader.service.ValidationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
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

/**
 * Position Upload API for FXAN UI.
 * <p>
 * Handles manual position uploads for non-FS accounts (not in MSPA).
 * Supports CSV file upload and JSON payload.
 */
@Slf4j
@RestController
@RequestMapping("/api/positions")
@RequiredArgsConstructor
@Tag(name = "Position Upload", description = "Manual position upload from FXAN UI")
public class UploadController {

    private final SnapshotService snapshotService;
    private final ValidationService validationService;

    /**
     * Upload positions via CSV file.
     * <p>
     * CSV format:
     * product_id,ticker,asset_class,quantity,price,currency
     * 5001,AAPL,EQUITY,100,150.25,USD
     */
    @PostMapping("/upload")
    @Operation(summary = "Upload positions from CSV file")
    public ResponseEntity<UploadResponse> uploadCsv(@RequestParam("file") MultipartFile file, @RequestParam("accountId") Integer accountId, @RequestParam(value = "clientId", defaultValue = "0") Integer clientId, Authentication auth) {

        String user = getUser(auth);
        log.info("Upload started: account={}, file={}, user={}", accountId, file.getOriginalFilename(), user);

        try {
            // Parse CSV
            List<PositionDTO> positions = parseCsv(file);

            if (positions.isEmpty()) {
                return ResponseEntity.badRequest().body(new UploadResponse(0, 0, List.of("No valid positions found in file")));
            }

            // Build snapshot
            AccountSnapshotDTO snapshot = new AccountSnapshotDTO(accountId, clientId, null,  // clientName
                    0,     // fundId
                    null,  // fundName
                    "USD", // baseCurrency
                    "ACC-" + accountId, "MANUAL", positions);

            // Validate
            ValidationResult result = validationService.validate(snapshot);
            if (result.hasErrors()) {
                List<String> errors = result.getErrors().stream().map(e -> e.code() + ": " + e.message()).toList();
                return ResponseEntity.badRequest().body(new UploadResponse(0, positions.size(), errors));
            }

            // Process
            snapshotService.processManualUpload(snapshot, user);

            log.info("Upload complete: account={}, positions={}", accountId, positions.size());
            return ResponseEntity.ok(new UploadResponse(positions.size(), 0, List.of()));

        } catch (Exception e) {
            log.error("Upload failed: account={}, error={}", accountId, e.getMessage());
            return ResponseEntity.internalServerError().body(new UploadResponse(0, 0, List.of(e.getMessage())));
        }
    }

    /**
     * Upload positions via JSON payload.
     */
    @PostMapping("/upload/json")
    @Operation(summary = "Upload positions from JSON payload")
    public ResponseEntity<UploadResponse> uploadJson(@Valid @RequestBody AccountSnapshotDTO snapshot, Authentication auth) {

        String user = getUser(auth);
        log.info("JSON upload started: account={}, user={}", snapshot.accountId(), user);

        try {
            // Validate
            ValidationResult result = validationService.validate(snapshot);
            if (result.hasErrors()) {
                List<String> errors = result.getErrors().stream().map(e -> e.code() + ": " + e.message()).toList();
                return ResponseEntity.badRequest().body(new UploadResponse(0, snapshot.positionCount(), errors));
            }

            // Process
            snapshotService.processManualUpload(snapshot, user);

            return ResponseEntity.ok(new UploadResponse(snapshot.positionCount(), 0, List.of()));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new UploadResponse(0, 0, List.of(e.getMessage())));
        } catch (Exception e) {
            log.error("JSON upload failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(new UploadResponse(0, 0, List.of(e.getMessage())));
        }
    }

    /**
     * Validate positions without saving.
     * Used by UI to show validation errors before submit.
     */
    @PostMapping("/validate")
    @Operation(summary = "Validate positions without saving")
    public ResponseEntity<ValidationResponse> validate(@RequestBody AccountSnapshotDTO snapshot) {
        ValidationResult result = validationService.validate(snapshot);

        return ResponseEntity.ok(new ValidationResponse(!result.hasErrors(), result.getErrors().stream().map(e -> e.message()).toList(), result.getWarnings().stream().map(w -> w.message()).toList()));
    }

    // ==================== HELPERS ====================

    private List<PositionDTO> parseCsv(MultipartFile file) throws Exception {
        List<PositionDTO> positions = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)); CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreEmptyLines(true).setTrim(true).build().parse(reader)) {

            for (CSVRecord record : parser) {
                try {
                    PositionDTO pos = new PositionDTO(parseInteger(record, "product_id"), getString(record, "ticker"), getString(record, "asset_class", "EQUITY"), getString(record, "currency", "USD"), parseBigDecimal(record, "quantity"), parseBigDecimal(record, "price"), null,  // marketValue - calculated
                            "MANUAL_UPLOAD", null   // externalRefId
                    );
                    positions.add(pos);
                } catch (Exception e) {
                    log.warn("Skipping invalid CSV row {}: {}", record.getRecordNumber(), e.getMessage());
                }
            }
        }

        return positions;
    }

    private Integer parseInteger(CSVRecord record, String column) {
        String value = record.get(column);
        return value != null && !value.isBlank() ? Integer.parseInt(value.trim()) : null;
    }

    private BigDecimal parseBigDecimal(CSVRecord record, String column) {
        String value = record.get(column);
        return value != null && !value.isBlank() ? new BigDecimal(value.trim()) : BigDecimal.ZERO;
    }

    private String getString(CSVRecord record, String column) {
        return record.get(column);
    }

    private String getString(CSVRecord record, String column, String defaultValue) {
        if (!record.isMapped(column)) return defaultValue;
        String value = record.get(column);
        return value != null && !value.isBlank() ? value.trim() : defaultValue;
    }

    private String getUser(Authentication auth) {
        return auth != null ? auth.getName() : "ANONYMOUS";
    }

    // ==================== RESPONSE DTOs ====================

    public record UploadResponse(int uploaded, int failed, List<String> errors) {
    }

    public record ValidationResponse(boolean valid, List<String> errors, List<String> warnings) {
    }
}
