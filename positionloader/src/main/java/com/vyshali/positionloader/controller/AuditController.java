package com.vyshali.positionloader.controller;

/*
 * Updated: 12/09/2025
 * Fixes: Swagger imports
 */

import com.vyshali.positionloader.repository.BitemporalRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
@Tag(name = "Audit", description = "Time-Travel and Historical Queries")
public class AuditController {

    private final BitemporalRepository bitemporalRepository;

    @GetMapping("/position/as-of")
    public ResponseEntity<BigDecimal> getPositionAsOf(@RequestParam Integer accountId, @RequestParam Integer productId, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime businessDate, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime systemTime) {

        BigDecimal qty = bitemporalRepository.getQuantityAsOf(accountId, productId, businessDate, systemTime);
        return ResponseEntity.ok(qty);
    }
}