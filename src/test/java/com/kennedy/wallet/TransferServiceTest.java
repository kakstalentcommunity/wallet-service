package com.kennedy.wallet;

import com.kennedy.wallet.config.WalletProperties;
import com.kennedy.wallet.domain.*;
import com.kennedy.wallet.repository.*;
import com.kennedy.wallet.service.*;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {
    @Mock UserRepository users;
    @Mock AccountRepository accounts;
    @Mock WalletTransactionRepository transactions;
    @Mock LedgerService ledger;
    @Mock FraudService fraud;@Mock AuditService audit;@Mock ApplicationEventPublisher events;
    FeeService fees;
    TransferService service;
    UUID actor;

    @BeforeEach void setUp() { WalletProperties props = new WalletProperties(); fees = new FeeService(props); service = new TransferService(users, accounts, transactions, ledger, fees, props,fraud,audit,events); actor = UUID.randomUUID(); }

    @Test void replayReturnsOriginalReceiptForSameRequest() throws Exception {
        String key = "retry-key-001";
        String hash = hash("+254712345678:500:KES");
        WalletTransaction original = new WalletTransaction("WLT-ORIGINAL", TransactionType.TRANSFER,
                TransactionStatus.COMPLETED, "KES", actor, 500L, 100L, key, hash);
        when(transactions.findByActorUserIdAndIdempotencyKey(actor, key)).thenReturn(Optional.of(original));

        var result = service.transfer(actor, key, 500, "0712345678");

        assertEquals("WLT-ORIGINAL", result.referenceCode());
        assertEquals(500L, result.amountMinor());
        assertEquals(100L, result.feeMinor());
    }

    @Test void differentBodyWithSameKeyIsRejected() throws Exception {
        String key = "retry-key-002";
        WalletTransaction original = new WalletTransaction("WLT-ORIGINAL", TransactionType.TRANSFER,
                TransactionStatus.COMPLETED, "KES", actor, 500L, 100L, key, hash("+254712345678:500:KES"));
        when(transactions.findByActorUserIdAndIdempotencyKey(actor, key)).thenReturn(Optional.of(original));
        assertThrows(TransferService.IdempotencyConflictException.class,
                () -> service.transfer(actor, key, 501, "0712345678"));
    }

    @Test void postsBalancedTransferAndAuditsSuccess() {
        User sender = new User("+254712345678", "hash");
        User receiver = new User("+254712345679", "hash");
        Account senderAccount = new Account(sender); senderAccount.applyBalanceDelta(5_000);
        Account receiverAccount = new Account(receiver);
        Account feeAccount = mock(Account.class);
        when(feeAccount.getId()).thenReturn(UUID.randomUUID()); when(feeAccount.isActive()).thenReturn(true);
        when(feeAccount.getCurrency()).thenReturn("KES");
        when(users.findById(sender.getId())).thenReturn(Optional.of(sender));
        when(users.findByPhone(receiver.getPhone())).thenReturn(Optional.of(receiver));
        when(accounts.findByUser_Id(sender.getId())).thenReturn(Optional.of(senderAccount));
        when(accounts.findByUser_Id(receiver.getId())).thenReturn(Optional.of(receiverAccount));
        when(accounts.findAllForUpdateByIdInOrderById(anyCollection())).thenReturn(List.of(senderAccount, receiverAccount));
        when(accounts.findByCode("FEE_REVENUE")).thenReturn(Optional.of(feeAccount));
        when(transactions.sumCompletedTransfersSince(eq(sender.getId()), any())).thenReturn(0L);
        when(fraud.evaluateTransfer(sender.getId(), receiver.getId(), 500)).thenReturn(new FraudService.Decision(FraudService.Outcome.ALLOW, java.util.List.of()));
        when(ledger.post(any(), anyList())).thenAnswer(call -> new WalletTransaction("WLT-NEW", TransactionType.TRANSFER,
                TransactionStatus.COMPLETED, "KES", sender.getId(), 500L, 100L, "success-key-01", "f".repeat(64)));

        var receipt = service.transfer(sender.getId(), "success-key-01", 500, receiver.getPhone());

        assertEquals("WLT-NEW", receipt.referenceCode());
        verify(ledger).post(argThat(draft -> draft.amountMinor() == 500L && draft.feeMinor() == 100L),
                argThat(lines -> lines.size() == 3 && lines.stream().mapToLong(line -> line.direction() == Direction.DEBIT ? line.amountMinor() : -line.amountMinor()).sum() == 0));
        verify(audit).record(eq(sender.getId()), eq("TRANSFER_COMPLETED"), anyMap());
        verify(events).publishEvent(any(PaymentCompletedEvent.class));
    }

    @Test void rejectsInsufficientFundsAndFraudBlocksBeforePosting() {
        User sender = new User("+254712345680", "hash");
        User receiver = new User("+254712345681", "hash");
        Account senderAccount = new Account(sender);
        Account receiverAccount = new Account(receiver);
        when(users.findById(sender.getId())).thenReturn(Optional.of(sender));
        when(users.findByPhone(receiver.getPhone())).thenReturn(Optional.of(receiver));
        when(accounts.findByUser_Id(sender.getId())).thenReturn(Optional.of(senderAccount));
        when(accounts.findByUser_Id(receiver.getId())).thenReturn(Optional.of(receiverAccount));
        when(accounts.findAllForUpdateByIdInOrderById(anyCollection())).thenReturn(List.of(senderAccount, receiverAccount));
        when(fraud.evaluateTransfer(sender.getId(), receiver.getId(), 500)).thenReturn(new FraudService.Decision(FraudService.Outcome.ALLOW, java.util.List.of()));
        when(transactions.sumCompletedTransfersSince(eq(sender.getId()), any())).thenReturn(0L);
        assertThrows(IllegalArgumentException.class, () -> service.transfer(sender.getId(), "funds-key-01", 500, receiver.getPhone()));
        verifyNoInteractions(ledger);

        when(fraud.evaluateTransfer(sender.getId(), receiver.getId(), 500)).thenReturn(new FraudService.Decision(FraudService.Outcome.BLOCK, java.util.List.of("TRANSFER_VELOCITY")));
        assertThrows(TransferService.FraudBlockedException.class, () -> service.transfer(sender.getId(), "block-key-01", 500, receiver.getPhone()));
        verify(audit).recordIndependent(eq(sender.getId()), eq("TRANSFER_BLOCKED"), anyMap());
    }

    private String hash(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
