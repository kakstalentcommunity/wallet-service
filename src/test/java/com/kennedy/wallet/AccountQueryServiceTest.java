package com.kennedy.wallet;

import com.kennedy.wallet.domain.*;
import com.kennedy.wallet.repository.*;
import com.kennedy.wallet.service.AccountQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountQueryServiceTest {
    @Mock AccountRepository accounts;
    @Mock WalletTransactionRepository transactions;
    @Mock LedgerEntryRepository entries;

    @Test void returnsOwnerBalanceAndReportsMissingAccount() {
        UUID userId = UUID.randomUUID();
        Account account = new Account(new User("+254712345678", "hash"));
        account.applyBalanceDelta(500);
        when(accounts.findByUser_Id(userId)).thenReturn(Optional.of(account));
        var service = new AccountQueryService(accounts, transactions, entries);

        var balance = service.myBalance(userId);

        assertEquals(account.getId(), balance.accountId());
        assertEquals(500, balance.balanceMinor());
        assertEquals("KES", balance.currency());
        when(accounts.findByUser_Id(userId)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.myBalance(userId));
    }

    @Test void mapsTransactionHistoryAndClampsPageSize() {
        UUID userId = UUID.randomUUID();
        Account account = new Account(new User("+254712345679", "hash"));
        var transaction = new WalletTransaction("WLT-HISTORY", TransactionType.TRANSFER, TransactionStatus.COMPLETED,
                "KES", userId, 500L, 100L, "history-key", "a".repeat(64));
        var entry = new LedgerEntry(transaction, account, Direction.DEBIT, 600, "KES");
        when(accounts.findByUser_Id(userId)).thenReturn(Optional.of(account));
        when(transactions.findHistory(eq(account.getId()), isNull(), isNull(), isNull(), isNull(), any()))
                .thenAnswer(invocation -> new PageImpl<>(List.of(transaction)));
        when(entries.findByTransaction_IdAndAccount_Id(transaction.getId(), account.getId())).thenReturn(List.of(entry));
        var service = new AccountQueryService(accounts, transactions, entries);

        var page = service.history(userId, null, null, null, null, -4, 200);

        assertEquals(600, page.getContent().getFirst().amountMinor());
        assertEquals(Direction.DEBIT, page.getContent().getFirst().direction());
        verify(transactions).findHistory(eq(account.getId()), isNull(), isNull(), isNull(), isNull(),
                argThat(pageable -> pageable.getPageNumber() == 0 && pageable.getPageSize() == 100));
    }

    @Test void rejectsInvalidDateRangesAndMissingHistoryAccount() {
        UUID userId = UUID.randomUUID();
        var service = new AccountQueryService(accounts, transactions, entries);
        var now = Instant.now();
        assertThrows(IllegalArgumentException.class, () -> service.history(userId, null, null, now, now, 0, 25));
        when(accounts.findByUser_Id(userId)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.history(userId, null, null, null, null, 0, 25));
    }
}
