package com.near.api.modules.wallet.service;

import com.near.api.modules.auth.entity.User;
import com.near.api.modules.auth.repository.UserRepository;
import com.near.api.modules.wallet.dto.request.RechargeRequest;
import com.near.api.modules.wallet.dto.request.TransferRequest;
import com.near.api.modules.wallet.dto.request.WithdrawalRequest;
import com.near.api.modules.wallet.dto.response.TransactionResponse;
import com.near.api.modules.wallet.dto.response.WalletResponse;
import com.near.api.modules.wallet.entity.Transaction;
import com.near.api.modules.wallet.entity.Transaction.TransactionStatus;
import com.near.api.modules.wallet.entity.Transaction.TransactionType;
import com.near.api.modules.wallet.entity.Wallet;
import com.near.api.modules.wallet.repository.TransactionRepository;
import com.near.api.modules.wallet.repository.WalletRepository;
import com.near.api.shared.exception.BadRequestException;
import com.near.api.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletServiceImpl implements WalletService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    // Tasa de conversión: 1 Near = 1.00 (unidad monetaria base)
    private static final BigDecimal NEAR_TO_CURRENCY = BigDecimal.ONE;
    
    // Comisión por retiro
    private static final BigDecimal WITHDRAWAL_COMMISSION = new BigDecimal("0.15"); // 15%

    // === Wallet ===

    @Override
    public WalletResponse getWallet(UUID userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet no encontrada"));
        return mapToWalletResponse(wallet);
    }

    @Override
    @Transactional
    public Wallet getOrCreateWallet(UUID userId) {
        return walletRepository.findByUserId(userId)
                .orElseGet(() -> createWallet(userId));
    }

    @Override
    @Transactional
    public WalletResponse createWalletForUser(UUID userId) {
        if (walletRepository.existsByUserId(userId)) {
            throw new BadRequestException("El usuario ya tiene una wallet");
        }
        Wallet wallet = createWallet(userId);
        return mapToWalletResponse(wallet);
    }

    private Wallet createWallet(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        Wallet wallet = Wallet.builder()
                .user(user)
                .totalBalance(BigDecimal.ZERO)
                .withdrawableBalance(BigDecimal.ZERO)
                .frozenBalance(BigDecimal.ZERO)
                .build();

        return walletRepository.save(wallet);
    }

    // === Recargas ===

    @Override
    @Transactional
    public TransactionResponse recharge(UUID userId, RechargeRequest request) {
        Wallet wallet = walletRepository.findByUserIdWithLock(userId)
                .orElseGet(() -> createWallet(userId));

        BigDecimal amount = BigDecimal.valueOf(request.getNearsAmount());

        // Crear transacción de recarga
        Transaction transaction = Transaction.builder()
                .wallet(wallet)
                .transactionType(TransactionType.RECHARGE)
                .amount(amount)
                .status(TransactionStatus.COMPLETED) // Asumimos pago exitoso desde frontend
                .paymentGateway(request.getPaymentGateway())
                .externalTransactionId(request.getExternalTransactionId())
                .description("Recarga de " + request.getNearsAmount() + " Nears")
                .completedAt(OffsetDateTime.now())
                .build();

        transaction = transactionRepository.save(transaction);

        // Actualizar balance
        wallet.addBalance(amount);
        walletRepository.save(wallet);

        log.info("Recarga exitosa: {} Nears para usuario {}", request.getNearsAmount(), userId);

        return mapToTransactionResponse(transaction);
    }

    // === Retiros ===

    @Override
    @Transactional
    public TransactionResponse requestWithdrawal(UUID userId, WithdrawalRequest request) {
        Wallet wallet = walletRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet no encontrada"));

        BigDecimal amount = BigDecimal.valueOf(request.getNearsAmount());

        // Verificar saldo suficiente
        if (wallet.getWithdrawableBalance().compareTo(amount) < 0) {
            throw new BadRequestException("Saldo insuficiente. Disponible: " + 
                    wallet.getWithdrawableBalance().intValue() + " Nears");
        }

        // Calcular comisión
        BigDecimal commission = amount.multiply(WITHDRAWAL_COMMISSION)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal netAmount = amount.subtract(commission);

        // Crear transacción de retiro (pendiente de procesamiento)
        Transaction transaction = Transaction.builder()
                .wallet(wallet)
                .transactionType(TransactionType.WITHDRAWAL)
                .amount(netAmount)
                .commissionAmount(commission)
                .commissionPercentage(WITHDRAWAL_COMMISSION.multiply(BigDecimal.valueOf(100)))
                .status(TransactionStatus.PENDING)
                .description("Retiro de " + request.getNearsAmount() + " Nears vía " + request.getWithdrawalMethod())
                .build();

        transaction = transactionRepository.save(transaction);

        // Descontar del balance
        wallet.subtractBalance(amount);
        walletRepository.save(wallet);

        // Crear transacción de comisión
        Transaction commissionTx = Transaction.builder()
                .wallet(wallet)
                .transactionType(TransactionType.COMMISSION)
                .amount(commission)
                .status(TransactionStatus.COMPLETED)
                .description("Comisión por retiro (15%)")
                .completedAt(OffsetDateTime.now())
                .build();

        transactionRepository.save(commissionTx);

        log.info("Solicitud de retiro: {} Nears (neto: {}) para usuario {}", 
                request.getNearsAmount(), netAmount, userId);

        return mapToTransactionResponse(transaction);
    }

    // === Transacciones de Requests ===

    @Override
    @Transactional
    public TransactionResponse processRequestPayment(UUID requesterId, UUID requestId, BigDecimal amount) {
        Wallet wallet = walletRepository.findByUserIdWithLock(requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet no encontrada"));

        if (wallet.getWithdrawableBalance().compareTo(amount) < 0) {
            throw new BadRequestException("Saldo insuficiente para crear la request");
        }

        // Congelar el monto (no se resta hasta que se complete)
        wallet.freezeBalance(amount);
        walletRepository.save(wallet);

        Transaction transaction = Transaction.builder()
                .wallet(wallet)
                .transactionType(TransactionType.REQUEST_PAYMENT)
                .amount(amount)
                .relatedRequestId(requestId)
                .status(TransactionStatus.PENDING)
                .description("Pago por request")
                .build();

        transaction = transactionRepository.save(transaction);

        log.info("Pago congelado: {} Nears para request {}", amount, requestId);

        return mapToTransactionResponse(transaction);
    }

    @Override
    @Transactional
    public TransactionResponse processRequestEarning(UUID responderId, UUID requestId, 
                                                      BigDecimal amount, BigDecimal commission) {
        Wallet wallet = walletRepository.findByUserIdWithLock(responderId)
                .orElseGet(() -> createWallet(responderId));

        BigDecimal netAmount = amount.subtract(commission);

        Transaction transaction = Transaction.builder()
                .wallet(wallet)
                .transactionType(TransactionType.REQUEST_EARNING)
                .amount(netAmount)
                .commissionAmount(commission)
                .relatedRequestId(requestId)
                .status(TransactionStatus.COMPLETED)
                .description("Ganancia por responder request")
                .completedAt(OffsetDateTime.now())
                .build();

        transaction = transactionRepository.save(transaction);

        // Agregar al balance
        wallet.addBalance(netAmount);
        walletRepository.save(wallet);

        log.info("Ganancia: {} Nears (comisión: {}) para usuario {} por request {}", 
                netAmount, commission, responderId, requestId);

        return mapToTransactionResponse(transaction);
    }

    @Override
    @Transactional
    public TransactionResponse processRequestRefund(UUID requesterId, UUID requestId, BigDecimal amount) {
        Wallet wallet = walletRepository.findByUserIdWithLock(requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet no encontrada"));

        // Descongelar y devolver al balance disponible
        wallet.unfreezeBalance(amount);
        walletRepository.save(wallet);

        Transaction transaction = Transaction.builder()
                .wallet(wallet)
                .transactionType(TransactionType.REQUEST_REFUND)
                .amount(amount)
                .relatedRequestId(requestId)
                .status(TransactionStatus.COMPLETED)
                .description("Reembolso por request cancelada/expirada")
                .completedAt(OffsetDateTime.now())
                .build();

        transaction = transactionRepository.save(transaction);

        log.info("Reembolso: {} Nears para usuario {} por request {}", amount, requesterId, requestId);

        return mapToTransactionResponse(transaction);
    }

    // === Congelar/Descongelar ===

    @Override
    @Transactional
    public void freezeBalance(UUID userId, BigDecimal amount) {
        Wallet wallet = walletRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet no encontrada"));
        wallet.freezeBalance(amount);
        walletRepository.save(wallet);
    }

    @Override
    @Transactional
    public void unfreezeBalance(UUID userId, BigDecimal amount) {
        Wallet wallet = walletRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet no encontrada"));
        wallet.unfreezeBalance(amount);
        walletRepository.save(wallet);
    }

    @Override
    @Transactional
    public void releaseFrozenBalance(UUID userId, BigDecimal amount) {
        Wallet wallet = walletRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet no encontrada"));
        wallet.releaseFrozenBalance(amount);
        walletRepository.save(wallet);
    }

    // === Historial ===

    @Override
    public Page<TransactionResponse> getTransactionHistory(UUID userId, Pageable pageable) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet no encontrada"));

        return transactionRepository.findByWalletIdOrderByCreatedAtDesc(wallet.getId(), pageable)
                .map(this::mapToTransactionResponse);
    }

    @Override
    public Page<TransactionResponse> getRechargeHistory(UUID userId, Pageable pageable) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet no encontrada"));

        return transactionRepository.findRechargeHistory(wallet.getId(), TransactionType.RECHARGE, pageable)
                .map(this::mapToTransactionResponse);
    }

    @Override
    public Page<TransactionResponse> getWithdrawalHistory(UUID userId, Pageable pageable) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet no encontrada"));

        return transactionRepository.findWithdrawalHistory(wallet.getId(), TransactionType.WITHDRAWAL, pageable)
                .map(this::mapToTransactionResponse);
    }

    // === Validaciones ===

    @Override
    public boolean hasEnoughBalance(UUID userId, BigDecimal amount) {
        return walletRepository.findByUserId(userId)
                .map(wallet -> wallet.getWithdrawableBalance().compareTo(amount) >= 0)
                .orElse(false);
    }

    // === Transferencias de Chat ===

    @Override
    @Transactional
    public TransactionResponse processTipTransfer(UUID senderId, UUID recipientId,
                                                  BigDecimal amount, String conversationId) {
        // Validar que no se envíe propina a sí mismo
        if (senderId.equals(recipientId)) {
            throw new BadRequestException("No puedes enviarte una propina a ti mismo");
        }

        // Obtener wallet del sender con lock
        Wallet senderWallet = walletRepository.findByUserIdWithLock(senderId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet del remitente no encontrada"));

        // Verificar saldo suficiente
        if (senderWallet.getWithdrawableBalance().compareTo(amount) < 0) {
            throw new BadRequestException("Saldo insuficiente para enviar propina");
        }

        // Obtener o crear wallet del recipient
        Wallet recipientWallet = walletRepository.findByUserIdWithLock(recipientId)
                .orElseGet(() -> createWallet(recipientId));

        // Descontar del sender
        senderWallet.subtractBalance(amount);
        walletRepository.save(senderWallet);

        // Crear transacción de envío (sender)
        Transaction sentTransaction = Transaction.builder()
                .wallet(senderWallet)
                .transactionType(TransactionType.TIP_SENT)
                .amount(amount.negate()) // Negativo porque es salida
                .status(TransactionStatus.COMPLETED)
                .description("Propina enviada en chat")
                .completedAt(OffsetDateTime.now())
                .build();
        transactionRepository.save(sentTransaction);

        // Agregar al recipient
        recipientWallet.addBalance(amount);
        walletRepository.save(recipientWallet);

        // Crear transacción de recepción (recipient)
        Transaction receivedTransaction = Transaction.builder()
                .wallet(recipientWallet)
                .transactionType(TransactionType.TIP_RECEIVED)
                .amount(amount) // Positivo porque es entrada
                .status(TransactionStatus.COMPLETED)
                .description("Propina recibida en chat")
                .completedAt(OffsetDateTime.now())
                .build();
        transactionRepository.save(receivedTransaction);

        log.info("Propina transferida: {} Nears de {} a {} en conversación {}",
                amount, senderId, recipientId, conversationId);

        return mapToTransactionResponse(sentTransaction);
    }

    @Override
    @Transactional
    public TransactionResponse processMediaPurchase(UUID buyerId, UUID sellerId,
                                                    BigDecimal amount, String conversationId,
                                                    String messageId) {
        // Validar que no se compre su propio contenido
        if (buyerId.equals(sellerId)) {
            throw new BadRequestException("No puedes comprar tu propio contenido");
        }

        // Obtener wallet del buyer con lock
        Wallet buyerWallet = walletRepository.findByUserIdWithLock(buyerId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet del comprador no encontrada"));

        // Verificar saldo suficiente
        if (buyerWallet.getWithdrawableBalance().compareTo(amount) < 0) {
            throw new BadRequestException("Saldo insuficiente para desbloquear contenido");
        }

        // Obtener o crear wallet del seller
        Wallet sellerWallet = walletRepository.findByUserIdWithLock(sellerId)
                .orElseGet(() -> createWallet(sellerId));

        // Calcular comisión (15%)
        BigDecimal commissionRate = new BigDecimal("0.15");
        BigDecimal commission = amount.multiply(commissionRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal sellerAmount = amount.subtract(commission);

        // Descontar del buyer
        buyerWallet.subtractBalance(amount);
        walletRepository.save(buyerWallet);

        // Crear transacción de compra (buyer)
        Transaction purchaseTransaction = Transaction.builder()
                .wallet(buyerWallet)
                .transactionType(TransactionType.MEDIA_PURCHASE)
                .amount(amount.negate()) // Negativo porque es salida
                .status(TransactionStatus.COMPLETED)
                .description("Desbloqueo de contenido multimedia")
                .completedAt(OffsetDateTime.now())
                .build();
        transactionRepository.save(purchaseTransaction);

        // Agregar al seller (menos comisión)
        sellerWallet.addBalance(sellerAmount);
        walletRepository.save(sellerWallet);

        // Crear transacción de venta (seller)
        Transaction saleTransaction = Transaction.builder()
                .wallet(sellerWallet)
                .transactionType(TransactionType.MEDIA_SALE)
                .amount(sellerAmount) // Positivo porque es entrada
                .commissionAmount(commission)
                .commissionPercentage(commissionRate.multiply(BigDecimal.valueOf(100)))
                .status(TransactionStatus.COMPLETED)
                .description("Venta de contenido multimedia")
                .completedAt(OffsetDateTime.now())
                .build();
        transactionRepository.save(saleTransaction);

        // Crear transacción de comisión
        Transaction commissionTransaction = Transaction.builder()
                .wallet(sellerWallet)
                .transactionType(TransactionType.COMMISSION)
                .amount(commission)
                .status(TransactionStatus.COMPLETED)
                .description("Comisión por venta de contenido (15%)")
                .completedAt(OffsetDateTime.now())
                .build();
        transactionRepository.save(commissionTransaction);

        log.info("Media desbloqueada: {} Nears de {} a {} (comisión: {}) - mensaje {} en conversación {}",
                amount, buyerId, sellerId, commission, messageId, conversationId);

        return mapToTransactionResponse(purchaseTransaction);
    }


    // === Mappers ===

    private WalletResponse mapToWalletResponse(Wallet wallet) {
        return WalletResponse.builder()
                .id(wallet.getId())
                .userId(wallet.getUser().getId())
                .totalBalance(wallet.getTotalBalance())
                .withdrawableBalance(wallet.getWithdrawableBalance())
                .frozenBalance(wallet.getFrozenBalance())
                .totalNears(wallet.getTotalBalance().intValue())
                .withdrawableNears(wallet.getWithdrawableBalance().intValue())
                .frozenNears(wallet.getFrozenBalance().intValue())
                .build();
    }

    private TransactionResponse mapToTransactionResponse(Transaction transaction) {
        return TransactionResponse.builder()
                .id(transaction.getId())
                .transactionType(transaction.getTransactionType())
                .amount(transaction.getAmount())
                .nearsAmount(transaction.getAmount().intValue())
                .commissionAmount(transaction.getCommissionAmount())
                .commissionPercentage(transaction.getCommissionPercentage())
                .status(transaction.getStatus())
                .description(transaction.getDescription())
                .paymentGateway(transaction.getPaymentGateway())
                .relatedRequestId(transaction.getRelatedRequestId())
                .createdAt(transaction.getCreatedAt())
                .completedAt(transaction.getCompletedAt())
                .build();
    }
}
