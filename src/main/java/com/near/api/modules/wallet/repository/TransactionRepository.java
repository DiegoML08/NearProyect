package com.near.api.modules.wallet.repository;

import com.near.api.modules.wallet.entity.Transaction;
import com.near.api.modules.wallet.entity.Transaction.TransactionStatus;
import com.near.api.modules.wallet.entity.Transaction.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Page<Transaction> findByWalletIdOrderByCreatedAtDesc(UUID walletId, Pageable pageable);

    Page<Transaction> findByWalletIdAndTransactionTypeOrderByCreatedAtDesc(
            UUID walletId, TransactionType type, Pageable pageable);

    List<Transaction> findByWalletIdAndStatusAndTransactionTypeIn(
            UUID walletId, TransactionStatus status, List<TransactionType> types);

    @Query("SELECT t FROM Transaction t WHERE t.wallet.id = :walletId " +
           "AND t.transactionType = :type ORDER BY t.createdAt DESC")
    Page<Transaction> findRechargeHistory(UUID walletId, TransactionType type, Pageable pageable);

    @Query("SELECT t FROM Transaction t WHERE t.wallet.id = :walletId " +
           "AND t.transactionType = :type ORDER BY t.createdAt DESC")
    Page<Transaction> findWithdrawalHistory(UUID walletId, TransactionType type, Pageable pageable);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.wallet.id = :walletId AND t.transactionType = :type " +
           "AND t.status = 'COMPLETED' AND t.createdAt >= :since")
    java.math.BigDecimal sumAmountByTypeAndPeriod(UUID walletId, TransactionType type, OffsetDateTime since);
}
