package com.vyshali.positionloader.dto;

/*
 * 12/1/25 - 22:57
 * @author Vyshali Prabananth Lal
 */

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PositionDetailDTO {
    private Integer productId;
    private String ticker;
    private String assetClass;
    private String issueCurrency;
    private BigDecimal quantity;
}
