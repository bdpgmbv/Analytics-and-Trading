package com.vyshali.positionloader.controller;

/*
 * 12/1/25 - 23:04
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.positionloader.dto.AccountSnapshotDTO;
import com.vyshali.positionloader.service.SnapshotService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ops")
@RequiredArgsConstructor
public class OpsController {
    private final SnapshotService snapshotService;

    @PostMapping("/eod/{accountId}")
    @PreAuthorize("hasAuthority('SCOPE_fxan.ops.write')")
    @Operation(summary = "Manual EOD Trigger")
    public ResponseEntity<String> triggerEod(@PathVariable Integer accountId) {
        snapshotService.processEodFromMspm(accountId);
        return ResponseEntity.ok("EOD Triggered");
    }

    @PostMapping("/intraday")
    @PreAuthorize("hasAuthority('SCOPE_fxan.ops.write')")
    public ResponseEntity<String> triggerIntraday(@RequestBody AccountSnapshotDTO dto) {
        snapshotService.processIntradayPayload(dto);
        return ResponseEntity.ok("Intraday Payload Processed");
    }
}