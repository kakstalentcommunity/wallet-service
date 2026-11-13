package com.kennedy.wallet.service;

import com.kennedy.wallet.domain.*;
import com.kennedy.wallet.repository.AccountRepository;
import com.kennedy.wallet.repository.LedgerEntryRepository;
import com.kennedy.wallet.repository.WalletTransactionRepository;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;

@Service
public class AccountQueryService {
    private final AccountRepository accounts;
    private final WalletTransactionRepository transactions;
    private final LedgerEntryRepository entries;
    public AccountQueryService(AccountRepository accounts, WalletTransactionRepository transactions, LedgerEntryRepository entries) {
        this.accounts = accounts; this.transactions = transactions; this.entries = entries;
    }

    @Transactional(readOnly = true)
    public AccountBalance myBalance(UUID userId) {
        Account account = accounts.findByUser_Id(userId).orElseThrow(() -> new IllegalArgumentException("Account not found"));
        return new AccountBalance(account.getId(), account.getBalanceMinor(), account.getCurrency());
    }

    @Transactional(readOnly = true)
    public Page<TransactionView> history(UUID userId, TransactionType type, TransactionStatus status,
                                         Instant from, Instant to, int page, int size) {
        if (from != null && to != null && !from.isBefore(to)) throw new IllegalArgumentException("Start date must be before end date");
        Account account = accounts.findByUser_Id(userId).orElseThrow(() -> new IllegalArgumentException("Account not found"));
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        return transactions.findHistory(account.getId(), type, status, from, to, pageable)
                .map(transaction -> toView(transaction, account.getId()));
    }

    private TransactionView toView(WalletTransaction transaction, UUID accountId) {
        var accountEntries = entries.findByTransaction_IdAndAccount_Id(transaction.getId(), accountId);
        long debits = 0, credits = 0;
        for (LedgerEntry entry : accountEntries) {
            if (entry.getDirection() == Direction.DEBIT) debits = Math.addExact(debits, entry.getAmountMinor());
            else credits = Math.addExact(credits, entry.getAmountMinor());
        }
        long amount = debits > 0 ? debits : credits;
        Direction direction = debits > 0 ? Direction.DEBIT : Direction.CREDIT;
        return new TransactionView(transaction.getId(), transaction.getReferenceCode(), transaction.getType(),
                transaction.getStatus(), direction, amount, transaction.getCurrency(), transaction.getCreatedAt());
    }

    public record AccountBalance(UUID accountId, long balanceMinor, String currency) { }
    public record TransactionView(UUID id, String referenceCode, TransactionType type, TransactionStatus status,
                                  Direction direction, long amountMinor, String currency, Instant createdAt) { }
}
