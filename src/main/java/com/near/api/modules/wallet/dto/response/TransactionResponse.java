package com.near.api.modules.wallet.dto.response;

import com.near.api.modules.wallet.entity.Transaction.TransactionStatus;
import com.near.api.modules.wallet.entity.Transaction.TransactionType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class TransactionResponse {

    private UUID id;
    private TransactionType transactionType;
    private BigDecimal amount;
    private Integer nearsAmount;
    private BigDecimal commissionAmount;
    private BigDecimal commissionPercentage;
    private TransactionStatus status;
    private String description;
    private String paymentGateway;
    private UUID relatedRequestId;
    private OffsetDateTime createdAt;
    private OffsetDateTime completedAt;
}
