package com.kennedy.wallet.service;

import com.kennedy.wallet.domain.Account;
import com.kennedy.wallet.repository.AccountRepository;
import com.kennedy.wallet.repository.LedgerEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class ReconciliationService {
    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);
    private final AccountRepository accounts;
    private final LedgerEntryRepository entries;
    public ReconciliationService(AccountRepository accounts, LedgerEntryRepository entries) { this.accounts = accounts; this.entries = entries; }

    /** Compares cached balances with the signed ledger total and returns every mismatch. */
    @Transactional(readOnly = true)
    public List<Mismatch> reconcile() {
        Map<UUID, Long> ledgerBalances = new HashMap<>();
        for (LedgerEntryRepository.LedgerBalance row : entries.findAccountNetBalances())
            ledgerBalances.put(row.getAccountId(), row.getNetBalance() == null ? 0L : row.getNetBalance());
        List<Mismatch> mismatches = new ArrayList<>();
        for (Account account : accounts.findAll()) {
            long ledger = ledgerBalances.getOrDefault(account.getId(), 0L);
            if (account.getBalanceMinor() != ledger) mismatches.add(new Mismatch(account.getId(), account.getBalanceMinor(), ledger));
        }
        return List.copyOf(mismatches);
    }

    @Scheduled(fixedDelayString = "${wallet.reconciliation.delay:PT5M}")
    public void scheduledReconciliation() {
        List<Mismatch> mismatches = reconcile();
        mismatches.forEach(mismatch -> log.error("Ledger reconciliation mismatch accountId={} cached={} ledger={}",
                mismatch.accountId(), mismatch.cachedBalanceMinor(), mismatch.ledgerBalanceMinor()));
    }
    public record Mismatch(UUID accountId, long cachedBalanceMinor, long ledgerBalanceMinor) { }
}
