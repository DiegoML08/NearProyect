package com.near.api.modules.wallet.entity;

import com.near.api.modules.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "wallets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "total_balance", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal totalBalance = BigDecimal.ZERO;

    @Column(name = "withdrawable_balance", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal withdrawableBalance = BigDecimal.ZERO;

    @Column(name = "frozen_balance", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal frozenBalance = BigDecimal.ZERO;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    // === Métodos de negocio ===

    public void addBalance(BigDecimal amount) {
        this.totalBalance = this.totalBalance.add(amount);
        this.withdrawableBalance = this.withdrawableBalance.add(amount);
    }

    public void subtractBalance(BigDecimal amount) {
        if (this.withdrawableBalance.compareTo(amount) < 0) {
            throw new IllegalStateException("Saldo insuficiente");
        }
        this.totalBalance = this.totalBalance.subtract(amount);
        this.withdrawableBalance = this.withdrawableBalance.subtract(amount);
    }

    public void freezeBalance(BigDecimal amount) {
        if (this.withdrawableBalance.compareTo(amount) < 0) {
            throw new IllegalStateException("Saldo insuficiente para congelar");
        }
        this.withdrawableBalance = this.withdrawableBalance.subtract(amount);
        this.frozenBalance = this.frozenBalance.add(amount);
    }

    public void unfreezeBalance(BigDecimal amount) {
        if (this.frozenBalance.compareTo(amount) < 0) {
            throw new IllegalStateException("Saldo congelado insuficiente");
        }
        this.frozenBalance = this.frozenBalance.subtract(amount);
        this.withdrawableBalance = this.withdrawableBalance.add(amount);
    }

    public void releaseFrozenBalance(BigDecimal amount) {
        if (this.frozenBalance.compareTo(amount) < 0) {
            throw new IllegalStateException("Saldo congelado insuficiente");
        }
        this.frozenBalance = this.frozenBalance.subtract(amount);
        this.totalBalance = this.totalBalance.subtract(amount);
    }
}
