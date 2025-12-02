package com.vyshali.positionloader.controller;

/*
 * 12/1/25 - 23:04
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.positionloader.service.SnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ops")
@RequiredArgsConstructor
public class OpsController {
    private final SnapshotService snapshotService;

    @PostMapping("/eod/{accountId}")
    public ResponseEntity<String> triggerEod(@PathVariable Integer accountId) {
        snapshotService.processEodFromMspm(accountId);
        return ResponseEntity.ok("EOD Triggered");
    }

    @PostMapping("/intraday/{accountId}")
    public ResponseEntity<String> triggerIntraday(@PathVariable Integer accountId) {
        snapshotService.processIntradayFromMspa(accountId);
        return ResponseEntity.ok("Intraday Triggered");
    }
}
