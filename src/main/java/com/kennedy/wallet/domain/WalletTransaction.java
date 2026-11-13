package com.kennedy.wallet.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transactions")
public class WalletTransaction {
    @Id private UUID id;
    @Column(name = "reference_code", nullable = false, unique = true, length = 32) private String referenceCode;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private TransactionType type;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private TransactionStatus status;
    @Column(nullable = false, length = 3) private String currency;
    @Column(name = "actor_user_id") private UUID actorUserId;
    @Column(name = "amount_minor") private Long amountMinor;
    @Column(name = "fee_minor") private Long feeMinor;
    @Column(name = "idempotency_key", length = 80) private String idempotencyKey;
    @Column(name = "request_hash", length = 64) private String requestHash;
    @Column(name = "reversal_of_id") private UUID reversalOfId;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();

    protected WalletTransaction() { }
    public WalletTransaction(String referenceCode, TransactionType type, TransactionStatus status, String currency, UUID actorUserId) {
        this.id = UUID.randomUUID(); this.referenceCode = referenceCode; this.type = type;
        this.status = status; this.currency = currency; this.actorUserId = actorUserId;
    }
    public WalletTransaction(String referenceCode, TransactionType type, TransactionStatus status, String currency,
                             UUID actorUserId, Long amountMinor, Long feeMinor, String idempotencyKey, String requestHash) {
        this(referenceCode, type, status, currency, actorUserId);
        this.amountMinor = amountMinor; this.feeMinor = feeMinor; this.idempotencyKey = idempotencyKey; this.requestHash = requestHash;
    }
    public WalletTransaction(String referenceCode, TransactionType type, TransactionStatus status, String currency,
                             UUID actorUserId, Long amountMinor, Long feeMinor, String idempotencyKey, String requestHash, UUID reversalOfId) {
        this(referenceCode,type,status,currency,actorUserId,amountMinor,feeMinor,idempotencyKey,requestHash);
        this.reversalOfId=reversalOfId;
    }
    public UUID getId() { return id; }
    public String getReferenceCode() { return referenceCode; }
    public TransactionType getType() { return type; }
    public TransactionStatus getStatus() { return status; }
    public String getCurrency() { return currency; }
    public UUID getActorUserId() { return actorUserId; }
    public Long getAmountMinor() { return amountMinor; }
    public Long getFeeMinor() { return feeMinor; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public UUID getReversalOfId() { return reversalOfId; }
    public Instant getCreatedAt() { return createdAt; }
    void changeStatus(TransactionStatus next) { status = next; }
}
