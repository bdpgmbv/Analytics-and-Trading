package com.vyshali.positionloader.dto;

/*
 * 12/1/25 - 22:57
 * @author Vyshali Prabananth Lal
 */

import lombok.Data;

import java.util.List;

@Data
public class AccountSnapshotDTO {
    private Integer clientId;
    private String clientName;
    private Integer fundId;
    private String fundName;
    private String baseCurrency;
    private Integer accountId;
    private String accountNumber;
    private String accountType;
    private List<PositionDetailDTO> positions;
}
