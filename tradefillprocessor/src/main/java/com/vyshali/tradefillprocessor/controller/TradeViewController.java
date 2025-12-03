package com.vyshali.tradefillprocessor.controller;

/*
 * 12/03/2025 - 1:16 PM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.tradefillprocessor.dto.FillDetailsDTO;
import com.vyshali.tradefillprocessor.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/trades")
@RequiredArgsConstructor
public class TradeViewController {
    private final TradeRepository tradeRepository;

    // UI calls this when user expands an Order row
    @GetMapping("/{orderId}/fills")
    public ResponseEntity<List<FillDetailsDTO>> getOrderFills(@PathVariable String orderId) {
        return ResponseEntity.ok(tradeRepository.getFillsForOrder(orderId));
    }
}
