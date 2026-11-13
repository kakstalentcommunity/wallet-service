package com.kennedy.wallet;

import com.kennedy.wallet.domain.Account;
import com.kennedy.wallet.domain.User;
import com.kennedy.wallet.repository.AccountRepository;
import com.kennedy.wallet.repository.LedgerEntryRepository;
import com.kennedy.wallet.service.ReconciliationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {
    @Mock AccountRepository accounts;
    @Mock LedgerEntryRepository entries;

    @Test void reportsCachedBalanceDifferentFromLedgerTotal() {
        Account account = new Account(new User("+254712345678", "encoded"));
        account.applyBalanceDelta(400);
        LedgerEntryRepository.LedgerBalance row = new LedgerEntryRepository.LedgerBalance() {
            public java.util.UUID getAccountId() { return account.getId(); }
            public Long getNetBalance() { return 250L; }
        };
        when(entries.findAccountNetBalances()).thenReturn(List.of(row));
        when(accounts.findAll()).thenReturn(List.of(account));

        var mismatches = new ReconciliationService(accounts, entries).reconcile();

        assertEquals(1, mismatches.size());
        assertEquals(400, mismatches.getFirst().cachedBalanceMinor());
        assertEquals(250, mismatches.getFirst().ledgerBalanceMinor());
    }
}
