package com.kennedy.wallet;

import com.kennedy.wallet.domain.*;
import com.kennedy.wallet.repository.AccountRepository;
import com.kennedy.wallet.repository.LedgerEntryRepository;
import com.kennedy.wallet.repository.WalletTransactionRepository;
import com.kennedy.wallet.service.LedgerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {
    @Mock AccountRepository accounts;
    @Mock WalletTransactionRepository transactions;
    @Mock LedgerEntryRepository entries;
    LedgerService ledger;
    Account debit;
    Account credit;

    @BeforeEach void setUp() {
        ledger = new LedgerService(accounts, transactions, entries);
        User user = new User("+254712345678", "encoded");
        debit = new Account(user);
        credit = new Account(new User("+254112345678", "encoded"));
        debit.applyBalanceDelta(1_000);
    }

    @Test void postsBalancedLinesAndAppliesCachedBalances() {
        LedgerService.TransactionDraft draft = new LedgerService.TransactionDraft(TransactionType.TRANSFER,
                TransactionStatus.COMPLETED, "KES", UUID.randomUUID());
        var lines = List.of(new LedgerService.LedgerLine(debit.getId(), Direction.DEBIT, 500, "KES"),
                new LedgerService.LedgerLine(credit.getId(), Direction.CREDIT, 500, "KES"));
        when(accounts.findAllForUpdateByIdInOrderById(any())).thenReturn(List.of(debit, credit));
        when(transactions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        WalletTransaction posted = ledger.post(draft, lines);

        assertNotNull(posted.getReferenceCode());
        assertEquals(500, debit.getBalanceMinor());
        assertEquals(500, credit.getBalanceMinor());
        verify(entries).saveAll(any());
        verify(accounts).saveAll(any());
    }

    @Test void rejectsUnbalancedTransactionBeforeWritingAnything() {
        var draft = new LedgerService.TransactionDraft(TransactionType.TRANSFER, TransactionStatus.COMPLETED, "KES", null);
        var lines = List.of(new LedgerService.LedgerLine(debit.getId(), Direction.DEBIT, 100, "KES"),
                new LedgerService.LedgerLine(credit.getId(), Direction.CREDIT, 99, "KES"));
        assertThrows(IllegalArgumentException.class, () -> ledger.post(draft, lines));
        verifyNoInteractions(accounts, transactions, entries);
    }

    @Test void rejectsNegativeWalletBalance() {
        var draft = new LedgerService.TransactionDraft(TransactionType.TRANSFER, TransactionStatus.COMPLETED, "KES", null);
        var lines = List.of(new LedgerService.LedgerLine(credit.getId(), Direction.DEBIT, 100, "KES"),
                new LedgerService.LedgerLine(debit.getId(), Direction.CREDIT, 100, "KES"));
        when(accounts.findAllForUpdateByIdInOrderById(any())).thenReturn(List.of(debit, credit));
        assertThrows(IllegalStateException.class, () -> ledger.post(draft, lines));
        verifyNoInteractions(transactions, entries);
    }

    @Test void rejectsNonPositiveEntryAmount() {
        var draft = new LedgerService.TransactionDraft(TransactionType.TRANSFER, TransactionStatus.COMPLETED, "KES", null);
        var lines = List.of(new LedgerService.LedgerLine(debit.getId(), Direction.DEBIT, 0, "KES"),
                new LedgerService.LedgerLine(credit.getId(), Direction.CREDIT, 0, "KES"));
        assertThrows(IllegalArgumentException.class, () -> ledger.post(draft, lines));
    }
}
