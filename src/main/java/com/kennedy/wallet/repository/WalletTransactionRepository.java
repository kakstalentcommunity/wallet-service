package com.kennedy.wallet.repository;

import com.kennedy.wallet.domain.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.UUID;
import java.util.Optional;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from WalletTransaction t where t.id=:id")
    Optional<WalletTransaction> findByIdForUpdate(@Param("id") UUID id);
    Optional<WalletTransaction> findByActorUserIdAndIdempotencyKey(UUID actorUserId, String idempotencyKey);
    @Query("select coalesce(sum(t.amountMinor), 0) from WalletTransaction t where t.actorUserId = :userId and t.type = com.kennedy.wallet.domain.TransactionType.TRANSFER and t.status = com.kennedy.wallet.domain.TransactionStatus.COMPLETED and t.createdAt >= :since")
    long sumCompletedTransfersSince(@Param("userId") UUID userId, @Param("since") Instant since);
    long countByActorUserIdAndTypeAndStatusAndCreatedAtGreaterThanEqual(UUID actorUserId, TransactionType type, TransactionStatus status, Instant since);
    @Query("select count(distinct received.account.user.id) from WalletTransaction t, LedgerEntry sent, LedgerEntry received where t.actorUserId=:actor and t.type=com.kennedy.wallet.domain.TransactionType.TRANSFER and t.status=com.kennedy.wallet.domain.TransactionStatus.COMPLETED and t.createdAt>=:since and sent.transaction=t and sent.direction=com.kennedy.wallet.domain.Direction.DEBIT and sent.account.user.id=:actor and received.transaction=t and received.direction=com.kennedy.wallet.domain.Direction.CREDIT and received.account.user.id is not null and not exists (select old.id from WalletTransaction old, LedgerEntry oldReceived where old.actorUserId=:actor and old.type=com.kennedy.wallet.domain.TransactionType.TRANSFER and old.createdAt<:since and oldReceived.transaction=old and oldReceived.direction=com.kennedy.wallet.domain.Direction.CREDIT and oldReceived.account.user.id=received.account.user.id)")
    long countNewRecipientsSince(@Param("actor") UUID actor, @Param("since") Instant since);
    @Query("select count(distinct received.account.user.id) from WalletTransaction t, LedgerEntry sent, LedgerEntry received where t.actorUserId=:actor and t.type=com.kennedy.wallet.domain.TransactionType.TRANSFER and t.status=com.kennedy.wallet.domain.TransactionStatus.COMPLETED and t.createdAt>=:since and sent.transaction=t and sent.direction=com.kennedy.wallet.domain.Direction.DEBIT and sent.account.user.id=:actor and received.transaction=t and received.direction=com.kennedy.wallet.domain.Direction.CREDIT and received.account.user.id=:receiver")
    long countRecipientTransfersSince(@Param("actor") UUID actor, @Param("receiver") UUID receiver, @Param("since") Instant since);
    @Query(value = "select t from WalletTransaction t where exists (select e.id from LedgerEntry e where e.transaction = t and e.account.id = :accountId) and (:type is null or t.type = :type) and (:status is null or t.status = :status) and (:fromDate is null or t.createdAt >= :fromDate) and (:toDate is null or t.createdAt < :toDate)",
           countQuery = "select count(t.id) from WalletTransaction t where exists (select e.id from LedgerEntry e where e.transaction = t and e.account.id = :accountId) and (:type is null or t.type = :type) and (:status is null or t.status = :status) and (:fromDate is null or t.createdAt >= :fromDate) and (:toDate is null or t.createdAt < :toDate)")
    Page<WalletTransaction> findHistory(@Param("accountId") UUID accountId, @Param("type") TransactionType type,
                                        @Param("status") TransactionStatus status, @Param("fromDate") Instant fromDate,
                                        @Param("toDate") Instant toDate, Pageable pageable);
}
