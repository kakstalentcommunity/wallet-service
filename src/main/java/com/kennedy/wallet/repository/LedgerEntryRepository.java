package com.kennedy.wallet.repository;

import com.kennedy.wallet.domain.Direction;
import com.kennedy.wallet.domain.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {
    List<LedgerEntry> findByAccount_Id(UUID accountId);
    List<LedgerEntry> findByTransaction_Id(UUID transactionId);
    List<LedgerEntry> findByTransaction_IdAndAccount_Id(UUID transactionId, UUID accountId);
    @Query("select e.account.id as accountId, sum(case when e.direction = com.kennedy.wallet.domain.Direction.CREDIT then e.amountMinor else -e.amountMinor end) as netBalance from LedgerEntry e group by e.account.id")
    List<LedgerBalance> findAccountNetBalances();
    interface LedgerBalance { UUID getAccountId(); Long getNetBalance(); }
}
