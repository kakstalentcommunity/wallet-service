package com.kennedy.wallet;

import com.kennedy.wallet.config.WalletProperties;
import com.kennedy.wallet.domain.*;
import com.kennedy.wallet.repository.WalletTransactionRepository;
import com.kennedy.wallet.service.FraudService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FraudServiceTest {
    @Mock WalletTransactionRepository transactions;
    @Test void blocksTransferWhenVelocityLimitIsAlreadyReached(){
        var props=new WalletProperties();var actor=UUID.randomUUID();var receiver=UUID.randomUUID();
        when(transactions.countByActorUserIdAndTypeAndStatusAndCreatedAtGreaterThanEqual(org.mockito.ArgumentMatchers.eq(actor),org.mockito.ArgumentMatchers.eq(TransactionType.TRANSFER),org.mockito.ArgumentMatchers.eq(TransactionStatus.COMPLETED),org.mockito.ArgumentMatchers.any(Instant.class))).thenReturn(10L);
        when(transactions.countNewRecipientsSince(org.mockito.ArgumentMatchers.eq(actor),org.mockito.ArgumentMatchers.any(Instant.class))).thenReturn(0L);
        var decision=new FraudService(transactions,props).evaluateTransfer(actor,receiver,100);
        assertEquals(FraudService.Outcome.BLOCK,decision.outcome());
        assertTrue(decision.signals().contains("TRANSFER_VELOCITY"));
    }
    @Test void flagsLargeAmountAndRepeatedLoginSignals(){
        var props=new WalletProperties();var user=new User("+254712345678","hash");
        user.failedLogin(Instant.now());user.failedLogin(Instant.now());user.failedLogin(Instant.now());user.failedLogin(Instant.now());user.failedLogin(Instant.now());
        var service=new FraudService(transactions,props);
        assertEquals(FraudService.Outcome.FLAG,service.evaluateFailedLogin(user).outcome());
        UUID actor=UUID.randomUUID();UUID recipient=UUID.randomUUID();
        when(transactions.countByActorUserIdAndTypeAndStatusAndCreatedAtGreaterThanEqual(eq(actor),eq(TransactionType.TRANSFER),eq(TransactionStatus.COMPLETED),any(Instant.class))).thenReturn(0L);
        when(transactions.countNewRecipientsSince(eq(actor),any(Instant.class))).thenReturn(0L);
        assertEquals(FraudService.Outcome.FLAG,service.evaluateTransfer(actor,recipient,20_000_000).outcome());
    }
}
