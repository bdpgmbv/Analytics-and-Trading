package com.vyshali.positionloader.controller;

/*
 * 12/1/25 - 23:04
 * @author Vyshali Prabananth Lal
 */
/*
 * Updated for Maker-Checker Implementation
 */

import com.vyshali.positionloader.dto.AccountSnapshotDTO;
import com.vyshali.positionloader.service.AuditService;
import com.vyshali.positionloader.service.DlqReplayService;
import com.vyshali.positionloader.service.SnapshotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate; // <--- Added for direct DB access
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/ops")
@RequiredArgsConstructor
@Tag(name = "Operations", description = "Manual triggers for Support Team (Audited)")
public class OpsController {

    private final SnapshotService snapshotService;
    private final AuditService auditService;
    private final DlqReplayService dlqReplayService;
    private final JdbcTemplate jdbcTemplate; // <--- Injected for Ops_Requests table

    // ==========================================================
    // 1. THE MAKER (User A initiates the Request)
    // ==========================================================
    @PostMapping("/eod/{accountId}")
    @PreAuthorize("hasAuthority('SCOPE_fxan.ops.write')")
    public ResponseEntity<String> requestEod(@PathVariable Integer accountId, Authentication auth) {
        String makerName = getName(auth);
        String requestId = "REQ-" + UUID.randomUUID().toString().substring(0, 8);

        log.info("MAKER: User {} is requesting EOD for Account {}", makerName, accountId);

        // Instead of running logic immediately, we INSERT a request
        String sql = """
                    INSERT INTO Ops_Requests (request_id, action_type, payload, requested_by, status)
                    VALUES (?, 'TRIGGER_EOD', ?, ?, 'PENDING')
                """;

        jdbcTemplate.update(sql, requestId, accountId.toString(), makerName);

        // Log the attempt in Audit Log
        auditService.logAction("REQUEST_EOD", accountId.toString(), makerName, "PENDING");

        return ResponseEntity.ok("Request submitted! ID: " + requestId + ". Ask a colleague to approve it.");
    }

    // ==========================================================
    // 2. THE CHECKER (User B approves the Request)
    // ==========================================================
    @PostMapping("/approve/{requestId}")
    @PreAuthorize("hasAuthority('SCOPE_fxan.ops.write')")
    public ResponseEntity<String> approveRequest(@PathVariable String requestId, Authentication auth) {
        String checkerName = getName(auth);

        // A. Find the Request
        Map<String, Object> req;
        try {
            req = jdbcTemplate.queryForMap("SELECT * FROM Ops_Requests WHERE request_id = ?", requestId);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Request ID not found: " + requestId);
        }

        String makerName = (String) req.get("requested_by");
        String status = (String) req.get("status");
        String payload = (String) req.get("payload");
        String actionType = (String) req.get("action_type");

        // B. VALIDATION: The "4-Eyes" Check
        if (!"PENDING".equals(status)) {
            return ResponseEntity.badRequest().body("Request is already " + status);
        }

        // CRITICAL CHECK: Maker cannot be Checker
        if (makerName.equals(checkerName)) {
            log.warn("SECURITY ALERT: User {} tried to approve their own request {}", checkerName, requestId);
            return ResponseEntity.status(403).body("Violation: Maker cannot be Checker. Ask someone else.");
        }

        // C. EXECUTION: Run the actual dangerous logic
        try {
            if ("TRIGGER_EOD".equals(actionType)) {
                Integer accountId = Integer.parseInt(payload);
                log.info("CHECKER: User {} approved EOD for Account {}. Executing now...", checkerName, accountId);

                // Call the actual service
                snapshotService.processEodFromMspm(accountId);

                auditService.logAction("APPROVE_EOD", accountId.toString(), checkerName, "SUCCESS");
            }

            // D. CLOSE OUT: Mark as approved
            jdbcTemplate.update("UPDATE Ops_Requests SET status = 'APPROVED', approved_by = ?, updated_at = CURRENT_TIMESTAMP WHERE request_id = ?", checkerName, requestId);

            return ResponseEntity.ok("Approved and Executed by " + checkerName);

        } catch (Exception e) {
            log.error("Execution failed after approval", e);
            auditService.logAction("APPROVE_EOD", payload, checkerName, "FAILED");
            return ResponseEntity.internalServerError().body("Execution failed: " + e.getMessage());
        }
    }

    // ==========================================================
    // OTHER METHODS (Kept as-is for now)
    // ==========================================================

    @PostMapping("/intraday")
    @PreAuthorize("hasAuthority('SCOPE_fxan.ops.write')")
    public ResponseEntity<String> triggerIntraday(@RequestBody AccountSnapshotDTO dto, Authentication auth) {
        String user = getName(auth);
        auditService.logAction("TRIGGER_INTRA", dto.accountId().toString(), user, "STARTED");
        try {
            snapshotService.processIntradayPayload(dto);
            auditService.logAction("TRIGGER_INTRA", dto.accountId().toString(), user, "SUCCESS");
            return ResponseEntity.ok("Intraday Processed");
        } catch (Exception e) {
            auditService.logAction("TRIGGER_INTRA", dto.accountId().toString(), user, "FAILED");
            throw e;
        }
    }

    @PostMapping("/dlq/replay")
    @PreAuthorize("hasAuthority('SCOPE_fxan.ops.admin')")
    public ResponseEntity<String> replayDlq(@RequestParam String topicName, Authentication auth) {
        String user = getName(auth);
        auditService.logAction("DLQ_REPLAY", topicName, user, "STARTED");
        try {
            int count = dlqReplayService.replayDlqMessages(topicName);
            auditService.logAction("DLQ_REPLAY", topicName, user, "SUCCESS");
            return ResponseEntity.ok("Replayed " + count + " messages.");
        } catch (Exception e) {
            auditService.logAction("DLQ_REPLAY", topicName, user, "FAILED");
            return ResponseEntity.internalServerError().body("Failed: " + e.getMessage());
        }
    }

    private String getName(Authentication auth) {
        return (auth != null) ? auth.getName() : "UNKNOWN";
    }
}