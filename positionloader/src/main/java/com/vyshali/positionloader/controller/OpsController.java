package com.vyshali.positionloader.controller;

/*
 * 12/1/25 - 23:04
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.positionloader.dto.AccountSnapshotDTO;
import com.vyshali.positionloader.repository.AuditRepository;
import com.vyshali.positionloader.service.DlqReplayService;
import com.vyshali.positionloader.service.SnapshotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/ops")
@RequiredArgsConstructor
@Tag(name = "Operations", description = "Manual triggers for Support Team (Audited)")
public class OpsController {

    private final SnapshotService snapshotService;
    private final AuditRepository auditRepo;
    private final DlqReplayService dlqReplayService;

    // --- EOD TRIGGER ---
    @PostMapping("/eod/{accountId}")
    @PreAuthorize("hasAuthority('SCOPE_fxan.ops.write')")
    @Operation(summary = "Manual EOD Trigger", description = "Wipes and reloads EOD data for an account.")
    public ResponseEntity<String> triggerEod(@PathVariable Integer accountId, Authentication auth) {
        String user = getName(auth);
        log.info("EOD Triggered by {} for {}", user, accountId);

        auditRepo.logAction("TRIGGER_EOD", accountId.toString(), user, "STARTED");
        try {
            snapshotService.processEodFromMspm(accountId);
            auditRepo.logAction("TRIGGER_EOD", accountId.toString(), user, "SUCCESS");
            return ResponseEntity.ok("EOD Processed");
        } catch (Exception e) {
            auditRepo.logAction("TRIGGER_EOD", accountId.toString(), user, "FAILED");
            throw e;
        }
    }

    // --- INTRADAY INJECTION ---
    @PostMapping("/intraday")
    @PreAuthorize("hasAuthority('SCOPE_fxan.ops.write')")
    @Operation(summary = "Manual Intraday Injection", description = "Injects position payload directly.")
    public ResponseEntity<String> triggerIntraday(@RequestBody AccountSnapshotDTO dto, Authentication auth) {
        String user = getName(auth);
        log.info("Intraday Injection by {} for {}", user, dto.accountId());

        auditRepo.logAction("TRIGGER_INTRA", dto.accountId().toString(), user, "STARTED");
        try {
            snapshotService.processIntradayPayload(dto);
            auditRepo.logAction("TRIGGER_INTRA", dto.accountId().toString(), user, "SUCCESS");
            return ResponseEntity.ok("Intraday Processed");
        } catch (Exception e) {
            auditRepo.logAction("TRIGGER_INTRA", dto.accountId().toString(), user, "FAILED");
            throw e;
        }
    }

    // --- DLQ REPLAY ---
    @PostMapping("/dlq/replay")
    @PreAuthorize("hasAuthority('SCOPE_fxan.ops.admin')")
    @Operation(summary = "Replay DLQ Messages", description = "Moves all messages from {topic}.DLT back to {topic}.")
    public ResponseEntity<String> replayDlq(@RequestParam String topicName, Authentication auth) {
        String user = getName(auth);
        log.info("DLQ Replay initiated for {} by {}", topicName, user);

        auditRepo.logAction("DLQ_REPLAY", topicName, user, "STARTED");
        try {
            int count = dlqReplayService.replayDlqMessages(topicName);
            auditRepo.logAction("DLQ_REPLAY", topicName, user, "SUCCESS");
            return ResponseEntity.ok("Successfully replayed " + count + " messages.");
        } catch (Exception e) {
            auditRepo.logAction("DLQ_REPLAY", topicName, user, "FAILED");
            return ResponseEntity.internalServerError().body("Replay failed: " + e.getMessage());
        }
    }

    private String getName(Authentication auth) {
        return (auth != null) ? auth.getName() : "UNKNOWN";
    }
}