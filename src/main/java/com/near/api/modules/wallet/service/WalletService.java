package com.near.api.modules.wallet.service;

import com.near.api.modules.wallet.dto.request.RechargeRequest;
import com.near.api.modules.wallet.dto.request.TransferRequest;
import com.near.api.modules.wallet.dto.request.WithdrawalRequest;
import com.near.api.modules.wallet.dto.response.TransactionResponse;
import com.near.api.modules.wallet.dto.response.WalletResponse;
import com.near.api.modules.wallet.entity.Wallet;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.UUID;

public interface WalletService {

    // Wallet
    WalletResponse getWallet(UUID userId);
    
    Wallet getOrCreateWallet(UUID userId);
    
    WalletResponse createWalletForUser(UUID userId);

    // Recargas
    TransactionResponse recharge(UUID userId, RechargeRequest request);

    // Retiros
    TransactionResponse requestWithdrawal(UUID userId, WithdrawalRequest request);

    // Transferencias internas (para requests)
    TransactionResponse processRequestPayment(UUID requesterId, UUID requestId, BigDecimal amount);
    
    TransactionResponse processRequestEarning(UUID responderId, UUID requestId, BigDecimal amount, BigDecimal commission);
    
    TransactionResponse processRequestRefund(UUID requesterId, UUID requestId, BigDecimal amount);

    // Congelar/Descongelar saldo
    void freezeBalance(UUID userId, BigDecimal amount);
    
    void unfreezeBalance(UUID userId, BigDecimal amount);
    
    void releaseFrozenBalance(UUID userId, BigDecimal amount);

    // Historial
    Page<TransactionResponse> getTransactionHistory(UUID userId, Pageable pageable);
    
    Page<TransactionResponse> getRechargeHistory(UUID userId, Pageable pageable);
    
    Page<TransactionResponse> getWithdrawalHistory(UUID userId, Pageable pageable);

    // Validaciones
    boolean hasEnoughBalance(UUID userId, BigDecimal amount);
}
