package com.vyshali.positionloader.dto;

/*
 * 12/02/2025 - 11:11 AM
 * @author Vyshali Prabananth Lal
 */

import java.time.LocalDate;

// FIX: Must be 'public record' to be accessible in the Service package
public record SignOffEventDTO(Integer clientId, LocalDate businessDate, String status, Integer totalAccounts) {
}
