package com.near.api.modules.wallet.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @Column(name = "transaction_type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private TransactionType transactionType;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "commission_amount", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal commissionAmount = BigDecimal.ZERO;

    @Column(name = "commission_percentage", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal commissionPercentage = BigDecimal.ZERO;

    @Column(name = "related_request_id")
    private UUID relatedRequestId;

    @Column(name = "external_transaction_id")
    private String externalTransactionId;

    @Column(name = "payment_gateway", length = 50)
    private String paymentGateway;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    // === Enums ===

    public enum TransactionType {
        RECHARGE,           // Recarga de Nears
        WITHDRAWAL,         // Retiro de Nears a dinero real
        REQUEST_PAYMENT,    // Pago por crear una request
        REQUEST_EARNING,    // Ganancia por responder una request
        REQUEST_REFUND,     // Reembolso de request cancelada/expirada
        COMMISSION,         // Comisión de la plataforma
        BONUS               // Bonificación/promoción
    }

    public enum TransactionStatus {
        PENDING,
        COMPLETED,
        FAILED,
        CANCELLED,
        REFUNDED
    }
}
