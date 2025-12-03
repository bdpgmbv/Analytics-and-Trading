package com.vyshali.mockupstream.controller;

/*
 * 12/02/2025 - 2:04 PM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.mockupstream.dto.AccountSnapshotDTO;
import com.vyshali.mockupstream.service.PositionGeneratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/mspm")
@RequiredArgsConstructor
public class MspmRestController {
    private final PositionGeneratorService generator;

    @GetMapping("/accounts/{id}/eod-snapshot")
    public ResponseEntity<AccountSnapshotDTO> getEod(@PathVariable Integer id) {
        return ResponseEntity.ok(generator.getCachedEod(id));
    }
}
