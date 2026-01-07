package com.near.api.modules.wallet.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class WalletResponse {

    private UUID id;
    private UUID userId;
    private BigDecimal totalBalance;
    private BigDecimal withdrawableBalance;
    private BigDecimal frozenBalance;
    
    // Conversión a Nears (enteros para mostrar al usuario)
    private Integer totalNears;
    private Integer withdrawableNears;
    private Integer frozenNears;
}
