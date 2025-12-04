package com.vyshali.positionloader.controller;

/*
 * 12/1/25 - 23:04
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.positionloader.dto.AccountSnapshotDTO;
import com.vyshali.positionloader.service.AuditService; // <--- CHANGED
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
    private final AuditService auditService; // <--- CHANGED FROM REPO
    private final DlqReplayService dlqReplayService;

    @PostMapping("/eod/{accountId}")
    @PreAuthorize("hasAuthority('SCOPE_fxan.ops.write')")
    public ResponseEntity<String> triggerEod(@PathVariable Integer accountId, Authentication auth) {
        String user = getName(auth);
        auditService.logAction("TRIGGER_EOD", accountId.toString(), user, "STARTED"); // <--- USE SERVICE
        try {
            snapshotService.processEodFromMspm(accountId);
            auditService.logAction("TRIGGER_EOD", accountId.toString(), user, "SUCCESS");
            return ResponseEntity.ok("EOD Processed");
        } catch (Exception e) {
            auditService.logAction("TRIGGER_EOD", accountId.toString(), user, "FAILED");
            throw e;
        }
    }

    // ... Update other methods (triggerIntraday, replayDlq) similarly to use auditService ...
    // (For brevity, ensure ALL auditRepo calls are replaced with auditService)

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