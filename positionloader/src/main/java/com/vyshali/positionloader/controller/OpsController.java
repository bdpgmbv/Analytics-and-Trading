package com.vyshali.positionloader.controller;

/*
 * 12/10/2025 - FIXED: Removed Swagger dependency, all methods now compile
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.positionloader.dto.AccountSnapshotDTO;
import com.vyshali.positionloader.repository.AuditRepository;
import com.vyshali.positionloader.repository.PositionRepository;
import com.vyshali.positionloader.service.EventService;
import com.vyshali.positionloader.service.SnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Operations controller with maker-checker pattern.
 * Includes audit queries and DLQ replay.
 */
@Slf4j
@RestController
@RequestMapping("/api/ops")
@RequiredArgsConstructor
public class OpsController {

    private final SnapshotService snapshotService;
    private final EventService eventService;
    private final AuditRepository audit;
    private final PositionRepository positions;

    // ==================== MAKER-CHECKER EOD ====================

    @PostMapping("/eod/{accountId}")
    @PreAuthorize("hasPermission(#accountId, 'TRIGGER_EOD')")
    public ResponseEntity<Map<String, String>> requestEod(@PathVariable Integer accountId, Authentication auth) {

        String maker = getUser(auth);
        String requestId = "REQ-" + UUID.randomUUID().toString().substring(0, 8);

        log.info("MAKER: {} requesting EOD for account {}", maker, accountId);

        audit.createRequest(requestId, "TRIGGER_EOD", accountId.toString(), maker);
        audit.log("REQUEST_EOD", accountId.toString(), maker, "PENDING");

        return ResponseEntity.ok(Map.of("requestId", requestId, "message", "Request submitted. Ask a colleague to approve."));
    }

    @PostMapping("/approve/{requestId}")
    @PreAuthorize("hasAuthority('SCOPE_fxan.ops.write')")
    public ResponseEntity<Map<String, String>> approveRequest(@PathVariable String requestId, Authentication auth) {

        String checker = getUser(auth);

        Map<String, Object> req = audit.getRequest(requestId);
        if (req == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Request not found"));
        }

        String maker = (String) req.get("requested_by");
        String status = (String) req.get("status");
        String payload = (String) req.get("payload");
        String action = (String) req.get("action_type");

        if (!"PENDING".equals(status)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Request already " + status));
        }

        if (maker.equals(checker)) {
            log.warn("SECURITY: {} tried to approve their own request", checker);
            return ResponseEntity.status(403).body(Map.of("error", "Maker cannot be checker"));
        }

        try {
            if ("TRIGGER_EOD".equals(action)) {
                Integer accountId = Integer.parseInt(payload);
                log.info("CHECKER: {} approved EOD for account {}", checker, accountId);

                snapshotService.processEod(accountId);
                audit.log("APPROVE_EOD", accountId.toString(), checker, "SUCCESS");
            }

            audit.approveRequest(requestId, checker);
            return ResponseEntity.ok(Map.of("message", "Approved and executed by " + checker));

        } catch (Exception e) {
            log.error("Execution failed after approval", e);
            audit.log("APPROVE_EOD", payload, checker, "FAILED");
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // ==================== MANUAL TRIGGERS ====================

    @PostMapping("/intraday")
    @PreAuthorize("hasAuthority('SCOPE_fxan.ops.write')")
    public ResponseEntity<Map<String, String>> triggerIntraday(@RequestBody AccountSnapshotDTO dto, Authentication auth) {

        String user = getUser(auth);
        audit.log("TRIGGER_INTRADAY", dto.accountId().toString(), user, "STARTED");

        try {
            snapshotService.processIntraday(dto);
            audit.log("TRIGGER_INTRADAY", dto.accountId().toString(), user, "SUCCESS");
            return ResponseEntity.ok(Map.of("message", "Intraday processed"));
        } catch (Exception e) {
            audit.log("TRIGGER_INTRADAY", dto.accountId().toString(), user, "FAILED");
            throw e;
        }
    }

    @PostMapping("/dlq/replay")
    @PreAuthorize("hasAuthority('SCOPE_fxan.ops.admin')")
    public ResponseEntity<Map<String, Object>> replayDlq(@RequestParam String topic, Authentication auth) {

        String user = getUser(auth);
        audit.log("DLQ_REPLAY", topic, user, "STARTED");

        try {
            int count = eventService.replayDlq(topic);
            audit.log("DLQ_REPLAY", topic, user, "SUCCESS");
            return ResponseEntity.ok(Map.of("replayed", count));
        } catch (Exception e) {
            audit.log("DLQ_REPLAY", topic, user, "FAILED");
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // ==================== AUDIT QUERIES ====================

    @GetMapping("/audit/position/as-of")
    public ResponseEntity<BigDecimal> getPositionAsOf(@RequestParam Integer accountId, @RequestParam Integer productId, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime businessDate, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime systemTime) {

        BigDecimal qty = positions.getQuantityAsOf(accountId, productId, Timestamp.valueOf(businessDate), Timestamp.valueOf(systemTime));

        return ResponseEntity.ok(qty);
    }

    // ==================== HEALTH CHECK ====================

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "positionloader"));
    }

    private String getUser(Authentication auth) {
        return auth != null ? auth.getName() : "ANONYMOUS";
    }
}