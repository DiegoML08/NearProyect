package com.near.api.modules.wallet.controller;

import com.near.api.modules.wallet.dto.request.RechargeRequest;
import com.near.api.modules.wallet.dto.request.WithdrawalRequest;
import com.near.api.modules.wallet.dto.response.TransactionResponse;
import com.near.api.modules.wallet.dto.response.WalletResponse;
import com.near.api.modules.wallet.service.WalletService;
import com.near.api.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    /**
     * Obtener información del wallet del usuario autenticado
     */
    @GetMapping()
    public ResponseEntity<ApiResponse<WalletResponse>> getMyWallet(
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        WalletResponse wallet = walletService.getWallet(userId);
        return ResponseEntity.ok(ApiResponse.success(wallet));
    }

    /**
     * Recargar Nears
     */
    @PostMapping("/recharge")
    public ResponseEntity<ApiResponse<TransactionResponse>> recharge(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody RechargeRequest request) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        TransactionResponse transaction = walletService.recharge(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Recarga exitosa", transaction));
    }

    /**
     * Solicitar retiro de Nears
     */
    @PostMapping("/withdraw")
    public ResponseEntity<ApiResponse<TransactionResponse>> withdraw(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody WithdrawalRequest request) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        TransactionResponse transaction = walletService.requestWithdrawal(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Solicitud de retiro creada", transaction));
    }

    /**
     * Historial de todas las transacciones
     */
    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<Page<TransactionResponse>>> getTransactionHistory(
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 20) Pageable pageable) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        Page<TransactionResponse> transactions = walletService.getTransactionHistory(userId, pageable);
        return ResponseEntity.ok(ApiResponse.success(transactions));
    }

    /**
     * Historial de recargas
     */
    @GetMapping("/transactions/recharges")
    public ResponseEntity<ApiResponse<Page<TransactionResponse>>> getRechargeHistory(
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 20) Pageable pageable) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        Page<TransactionResponse> transactions = walletService.getRechargeHistory(userId, pageable);
        return ResponseEntity.ok(ApiResponse.success(transactions));
    }

    /**
     * Historial de retiros
     */
    @GetMapping("/transactions/withdrawals")
    public ResponseEntity<ApiResponse<Page<TransactionResponse>>> getWithdrawalHistory(
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 20) Pageable pageable) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        Page<TransactionResponse> transactions = walletService.getWithdrawalHistory(userId, pageable);
        return ResponseEntity.ok(ApiResponse.success(transactions));
    }

    /**
     * Verificar si tiene saldo suficiente
     */
    @GetMapping("/check-balance/{amount}")
    public ResponseEntity<ApiResponse<Boolean>> checkBalance(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Integer amount) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        boolean hasBalance = walletService.hasEnoughBalance(userId, 
                java.math.BigDecimal.valueOf(amount));
        return ResponseEntity.ok(ApiResponse.success(hasBalance));
    }
}
