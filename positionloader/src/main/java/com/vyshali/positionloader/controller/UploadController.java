package com.vyshali.positionloader.controller;

/*
 * AMPLIFIED: Added batch size limits to prevent OOM and abuse
 *
 * LIMITS:
 * - Max positions per upload: 10,000
 * - Max file size: 10MB
 * - Max accounts per batch: 100
 *
 * WHY NEEDED:
 * - Unbounded uploads can exhaust memory
 * - Huge payloads can timeout or fail
 * - Protects downstream systems (DB, Kafka)
 */

import com.vyshali.positionloader.dto.AccountSnapshotDTO;
import com.vyshali.positionloader.dto.PositionDTO;
import com.vyshali.positionloader.exception.UploadLimitExceededException;
import com.vyshali.positionloader.service.IdempotencyService;
import com.vyshali.positionloader.service.SnapshotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/positions")
@RequiredArgsConstructor
@Tag(name = "Position Upload", description = "Upload positions via REST API")
public class UploadController {

    private final SnapshotService snapshotService;
    private final IdempotencyService idempotencyService;

    // ═══════════════════════════════════════════════════════════════════════
    // CONFIGURABLE LIMITS
    // ═══════════════════════════════════════════════════════════════════════
    @Value("${upload.max-positions:10000}")
    private int maxPositions;

    @Value("${upload.max-file-size-mb:10}")
    private int maxFileSizeMb;

    @Value("${upload.max-accounts-per-batch:100}")
    private int maxAccountsPerBatch;

    /**
     * Upload positions via JSON.
     */
    @PostMapping("/upload")
    @Operation(summary = "Upload positions (JSON)")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Positions uploaded successfully"), @ApiResponse(responseCode = "400", description = "Invalid input or limits exceeded"), @ApiResponse(responseCode = "409", description = "Duplicate upload detected"), @ApiResponse(responseCode = "413", description = "Payload too large")})
    public ResponseEntity<?> uploadPositions(@Parameter(description = "Account ID") @RequestParam Integer accountId, @RequestBody List<PositionDTO> positions) {

        // ═══════════════════════════════════════════════════════════
        // LIMIT CHECK: Max positions per upload
        // ═══════════════════════════════════════════════════════════
        if (positions.size() > maxPositions) {
            throw new UploadLimitExceededException("Position count " + positions.size() + " exceeds limit of " + maxPositions);
        }

        // Idempotency check
        String fileId = idempotencyService.generatePositionIdentifier(positions.size(), positions.isEmpty() ? null : positions.get(0).getProductId());

        if (!idempotencyService.isNewUpload(accountId, fileId)) {
            log.warn("Duplicate upload detected: accountId={}, fileId={}", accountId, fileId);
            return ResponseEntity.status(409).body(Map.of("error", "Duplicate upload", "message", "This exact payload was already uploaded recently"));
        }

        // Process upload
        int processed = snapshotService.processUpload(accountId, positions);

        return ResponseEntity.ok(Map.of("accountId", accountId, "positionsReceived", positions.size(), "positionsProcessed", processed));
    }

    /**
     * Upload positions via CSV file.
     */
    @PostMapping("/upload/csv")
    @Operation(summary = "Upload positions (CSV file)")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "CSV processed successfully"), @ApiResponse(responseCode = "400", description = "Invalid CSV or limits exceeded"), @ApiResponse(responseCode = "409", description = "Duplicate file detected"), @ApiResponse(responseCode = "413", description = "File too large")})
    public ResponseEntity<?> uploadCsv(@Parameter(description = "Account ID") @RequestParam Integer accountId, @RequestPart("file") MultipartFile file) {

        // ═══════════════════════════════════════════════════════════
        // LIMIT CHECK: Max file size
        // ═══════════════════════════════════════════════════════════
        long maxBytes = maxFileSizeMb * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw new UploadLimitExceededException("File size " + (file.getSize() / 1024 / 1024) + "MB exceeds limit of " + maxFileSizeMb + "MB");
        }

        // Idempotency check
        String fileId = idempotencyService.generateFileIdentifier(file.getOriginalFilename(), file.getSize());

        if (!idempotencyService.isNewUpload(accountId, fileId)) {
            log.warn("Duplicate CSV upload: accountId={}, file={}", accountId, file.getOriginalFilename());
            return ResponseEntity.status(409).body(Map.of("error", "Duplicate upload", "message", "This file was already uploaded recently"));
        }

        // Parse CSV and check position count
        List<PositionDTO> positions = parseCsv(file);

        // ═══════════════════════════════════════════════════════════
        // LIMIT CHECK: Max positions in file
        // ═══════════════════════════════════════════════════════════
        if (positions.size() > maxPositions) {
            throw new UploadLimitExceededException("CSV contains " + positions.size() + " positions, exceeds limit of " + maxPositions);
        }

        int processed = snapshotService.processUpload(accountId, positions);

        return ResponseEntity.ok(Map.of("accountId", accountId, "fileName", file.getOriginalFilename(), "positionsReceived", positions.size(), "positionsProcessed", processed));
    }

    /**
     * Batch upload for multiple accounts.
     */
    @PostMapping("/upload/batch")
    @Operation(summary = "Upload positions for multiple accounts")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Batch processed"), @ApiResponse(responseCode = "400", description = "Invalid input or limits exceeded")})
    public ResponseEntity<?> uploadBatch(@RequestBody List<AccountSnapshotDTO> snapshots) {

        // ═══════════════════════════════════════════════════════════
        // LIMIT CHECK: Max accounts per batch
        // ═══════════════════════════════════════════════════════════
        if (snapshots.size() > maxAccountsPerBatch) {
            throw new UploadLimitExceededException("Batch contains " + snapshots.size() + " accounts, exceeds limit of " + maxAccountsPerBatch);
        }

        // Check total positions across all accounts
        int totalPositions = snapshots.stream().mapToInt(s -> s.getPositions() != null ? s.getPositions().size() : 0).sum();

        if (totalPositions > maxPositions) {
            throw new UploadLimitExceededException("Total positions " + totalPositions + " exceeds limit of " + maxPositions);
        }

        // Process each account
        int accountsProcessed = 0;
        int positionsProcessed = 0;
        List<Integer> failedAccounts = new ArrayList<>();

        for (AccountSnapshotDTO snapshot : snapshots) {
            try {
                int count = snapshotService.processUpload(snapshot.getAccountId(), snapshot.getPositions());
                accountsProcessed++;
                positionsProcessed += count;
            } catch (Exception e) {
                log.error("Failed to process account {}: {}", snapshot.getAccountId(), e.getMessage());
                failedAccounts.add(snapshot.getAccountId());
            }
        }

        return ResponseEntity.ok(Map.of("accountsReceived", snapshots.size(), "accountsProcessed", accountsProcessed, "positionsProcessed", positionsProcessed, "failedAccounts", failedAccounts));
    }

    /**
     * Parse CSV file to positions.
     * Expected format: productId,quantity,price,currency
     */
    private List<PositionDTO> parseCsv(MultipartFile file) {
        List<PositionDTO> positions = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {

            String line;
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {
                // Skip header row
                if (firstLine) {
                    firstLine = false;
                    continue;
                }

                String[] parts = line.split(",");
                if (parts.length >= 4) {
                    PositionDTO pos = new PositionDTO();
                    pos.setProductId(parts[0].trim());
                    pos.setQuantity(new java.math.BigDecimal(parts[1].trim()));
                    pos.setPrice(new java.math.BigDecimal(parts[2].trim()));
                    pos.setCurrency(parts[3].trim());
                    positions.add(pos);
                }
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse CSV: " + e.getMessage());
        }

        return positions;
    }
}