package com.kennedy.wallet.service;

import com.kennedy.wallet.domain.*;
import com.kennedy.wallet.repository.AccountRepository;
import com.kennedy.wallet.repository.LedgerEntryRepository;
import com.kennedy.wallet.repository.WalletTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class LedgerService {
    private final AccountRepository accounts;
    private final WalletTransactionRepository transactions;
    private final LedgerEntryRepository entries;

    public LedgerService(AccountRepository accounts, WalletTransactionRepository transactions, LedgerEntryRepository entries) {
        this.accounts = accounts; this.transactions = transactions; this.entries = entries;
    }

    /** Posts a balanced, immutable double-entry transaction and updates its cached account balances atomically. */
    @Transactional
    public WalletTransaction post(TransactionDraft draft, List<LedgerLine> lines) {
        Objects.requireNonNull(draft, "draft");
        WalletTransaction transaction = new WalletTransaction(referenceCode(), draft.type(), draft.status(), draft.currency(),
                draft.actorUserId(), draft.amountMinor(), draft.feeMinor(), draft.idempotencyKey(), draft.requestHash(), draft.reversalOfId());
        return postEntries(transaction, lines);
    }

    /** Completes a pending transaction by appending its balanced ledger entries. */
    @Transactional
    public WalletTransaction postExisting(WalletTransaction transaction, List<LedgerLine> lines) {
        if (transaction.getStatus() != TransactionStatus.PENDING) throw new IllegalStateException("Only pending transactions can receive ledger entries");
        TransactionStateMachine.transition(transaction, TransactionStatus.COMPLETED);
        return postEntries(transaction, lines);
    }

    private WalletTransaction postEntries(WalletTransaction transaction, List<LedgerLine> lines) {
        Objects.requireNonNull(transaction, "transaction");
        if (lines == null || lines.size() < 2) throw new IllegalArgumentException("At least two ledger entries are required");
        long debits = 0, credits = 0;
        Set<UUID> accountIds = new TreeSet<>();
        for (LedgerLine line : lines) {
            if (line == null || line.accountId() == null || line.direction() == null || line.amountMinor() <= 0)
                throw new IllegalArgumentException("Ledger entries must have an account, direction, and positive amount");
            if (!transaction.getCurrency().equals(line.currency())) throw new IllegalArgumentException("Transaction must use one currency");
            if (line.direction() == Direction.DEBIT) debits = Math.addExact(debits, line.amountMinor());
            else credits = Math.addExact(credits, line.amountMinor());
            accountIds.add(line.accountId());
        }
        if (debits != credits) throw new IllegalArgumentException("Transaction debits must equal credits");
        List<Account> lockedAccounts = accounts.findAllForUpdateByIdInOrderById(accountIds);
        if (lockedAccounts.size() != accountIds.size()) throw new IllegalArgumentException("One or more accounts do not exist");
        Map<UUID, Account> byId = new HashMap<>();
        lockedAccounts.forEach(account -> byId.put(account.getId(), account));
        Map<UUID, Long> deltas = new HashMap<>();
        for (LedgerLine line : lines) {
            Account account = byId.get(line.accountId());
            if (!account.isActive()) throw new IllegalArgumentException("Account is inactive");
            if (!account.getCurrency().equals(line.currency())) throw new IllegalArgumentException("Ledger currency does not match account currency");
            long delta = line.direction() == Direction.CREDIT ? line.amountMinor() : -line.amountMinor();
            deltas.merge(line.accountId(), delta, Math::addExact);
        }
        deltas.forEach((id, delta) -> byId.get(id).applyBalanceDelta(delta));

        WalletTransaction persisted = transactions.save(transaction);
        List<LedgerEntry> newEntries = lines.stream().map(line -> new LedgerEntry(persisted, byId.get(line.accountId()),
                line.direction(), line.amountMinor(), line.currency())).toList();
        entries.saveAll(newEntries);
        accounts.saveAll(lockedAccounts);
        return persisted;
    }

    private String referenceCode() { return "WLT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase(Locale.ROOT); }
    public record TransactionDraft(TransactionType type, TransactionStatus status, String currency, UUID actorUserId,
                                   Long amountMinor, Long feeMinor, String idempotencyKey, String requestHash, UUID reversalOfId) {
        public TransactionDraft(TransactionType type, TransactionStatus status, String currency, UUID actorUserId) {
            this(type, status, currency, actorUserId, null, null, null, null, null);
        }
        public TransactionDraft(TransactionType type, TransactionStatus status, String currency, UUID actorUserId,
                                Long amountMinor, Long feeMinor, String idempotencyKey, String requestHash) {
            this(type,status,currency,actorUserId,amountMinor,feeMinor,idempotencyKey,requestHash,null);
        }
        public TransactionDraft { Objects.requireNonNull(type); Objects.requireNonNull(status); if (currency == null || !currency.matches("[A-Z]{3}")) throw new IllegalArgumentException("Invalid currency"); }
    }
    public record LedgerLine(UUID accountId, Direction direction, long amountMinor, String currency) { }
}
