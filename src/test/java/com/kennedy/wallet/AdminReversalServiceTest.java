package com.kennedy.wallet;

import com.kennedy.wallet.domain.*;
import com.kennedy.wallet.repository.LedgerEntryRepository;
import com.kennedy.wallet.repository.WalletTransactionRepository;
import com.kennedy.wallet.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminReversalServiceTest {
    @Mock WalletTransactionRepository transactions;
    @Mock LedgerEntryRepository entries;
    @Mock LedgerService ledger;
    @Mock AuditService audit;
    @Mock ApplicationEventPublisher events;

    @Test void reversalAppendsOppositeEntriesAndCanBeReplayed() throws Exception {
        UUID actor = UUID.randomUUID(), admin = UUID.randomUUID();
        var original = new WalletTransaction("WLT-ORIGINAL", TransactionType.TRANSFER, TransactionStatus.COMPLETED,
                "KES", actor, 500L, 100L, "transfer-key", "a".repeat(64));
        var debit = mock(Account.class); var credit = mock(Account.class);
        when(debit.getId()).thenReturn(UUID.randomUUID()); when(credit.getId()).thenReturn(UUID.randomUUID());
        when(entries.findByTransaction_Id(original.getId())).thenReturn(List.of(
                new LedgerEntry(original, debit, Direction.DEBIT, 600, "KES"),
                new LedgerEntry(original, credit, Direction.CREDIT, 500, "KES"),
                new LedgerEntry(original, credit, Direction.CREDIT, 100, "KES")));
        when(transactions.findByActorUserIdAndIdempotencyKey(admin, "reverse-key-01")).thenReturn(Optional.empty());
        when(transactions.findByIdForUpdate(original.getId())).thenReturn(Optional.of(original));
        var reversal = new WalletTransaction("WLT-REVERSAL", TransactionType.REVERSAL, TransactionStatus.COMPLETED,
                "KES", admin, 500L, 100L, "reverse-key-01", hash(original.getId() + ":ADMIN_REVERSAL"), original.getId());
        when(ledger.post(any(), anyList())).thenReturn(reversal);
        var service = new AdminReversalService(transactions, entries, ledger, audit, events);

        var result = service.reverse(admin, "reverse-key-01", original.getId());
        when(transactions.findByActorUserIdAndIdempotencyKey(admin, "reverse-key-01")).thenReturn(Optional.of(reversal));
        var replay = service.reverse(admin, "reverse-key-01", original.getId());

        assertEquals(TransactionStatus.REVERSED, original.getStatus());
        assertEquals(reversal.getId(), result.reversalTransactionId());
        assertEquals(result.reversalTransactionId(), replay.reversalTransactionId());
        verify(ledger).post(argThat(draft -> draft.type() == TransactionType.REVERSAL && original.getId().equals(draft.reversalOfId())),
                argThat(lines -> lines.size() == 3 && lines.getFirst().direction() == Direction.CREDIT));
        verify(audit).record(eq(admin), eq("TRANSACTION_REVERSED"), anyMap());
    }

    @Test void rejectsKeyConflictAndNonCompletedOriginal() throws Exception {
        UUID admin = UUID.randomUUID(), id = UUID.randomUUID();
        var service = new AdminReversalService(transactions, entries, ledger, audit, events);
        var prior = new WalletTransaction("WLT-REV", TransactionType.REVERSAL, TransactionStatus.COMPLETED,
                "KES", admin, 100L, 0L, "replay-key-01", "0".repeat(64), id);
        when(transactions.findByActorUserIdAndIdempotencyKey(admin, "replay-key-01")).thenReturn(Optional.of(prior));
        assertThrows(TransferService.IdempotencyConflictException.class, () -> service.reverse(admin, "replay-key-01", id));
        assertThrows(IllegalArgumentException.class, () -> service.reverse(admin, "short", id));

        var pending = new WalletTransaction("WLT-PENDING", TransactionType.TRANSFER, TransactionStatus.PENDING, "KES", UUID.randomUUID());
        when(transactions.findByActorUserIdAndIdempotencyKey(admin, "other-key-01")).thenReturn(Optional.empty());
        when(transactions.findByIdForUpdate(pending.getId())).thenReturn(Optional.of(pending));
        assertThrows(IllegalArgumentException.class, () -> service.reverse(admin, "other-key-01", pending.getId()));
    }

    private String hash(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
