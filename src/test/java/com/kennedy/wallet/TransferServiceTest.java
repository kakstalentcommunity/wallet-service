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

    private String hash(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
