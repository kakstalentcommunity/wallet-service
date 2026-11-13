package com.kennedy.wallet.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "transaction_id", nullable = false) private WalletTransaction transaction;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "account_id", nullable = false) private Account account;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 6) private Direction direction;
    @Column(name = "amount_minor", nullable = false) private long amountMinor;
    @Column(nullable = false, length = 3) private String currency;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();

    protected LedgerEntry() { }
    public LedgerEntry(WalletTransaction transaction, Account account, Direction direction, long amountMinor, String currency) {
        this.id = UUID.randomUUID(); this.transaction = transaction; this.account = account;
        this.direction = direction; this.amountMinor = amountMinor; this.currency = currency;
    }
    public UUID getId() { return id; }
    public WalletTransaction getTransaction() { return transaction; }
    public Account getAccount() { return account; }
    public Direction getDirection() { return direction; }
    public long getAmountMinor() { return amountMinor; }
    public String getCurrency() { return currency; }
    public Instant getCreatedAt() { return createdAt; }
}
