package com.kennedy.wallet;

import com.kennedy.wallet.config.WalletProperties;
import com.kennedy.wallet.domain.TransactionStatus;
import com.kennedy.wallet.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WithdrawalServiceTest {
    @Mock WithdrawalLifecycleService lifecycle;
    @Mock B2cClient b2c;

    @Test void sendsOnlyNewRequestsAndCompletesProviderCallback() {
        UUID id = UUID.randomUUID(), requestId = UUID.randomUUID();
        var started = new WithdrawalLifecycleService.Started(requestId, id, "WLT-WITHDRAW", TransactionStatus.PENDING, true);
        when(lifecycle.createPending(eq(id), eq("withdraw-key-01"), eq("+254712345678"), eq(1_000L), anyString())).thenReturn(started);
        when(b2c.submit("+254712345678", 1_000, "WLT-WITHDRAW"))
                .thenReturn(CompletableFuture.completedFuture(new B2cClient.Result(true, "provider-1")));
        var service = new WithdrawalService(lifecycle, b2c, new WalletProperties());

        var receipt = service.withdraw(id, "withdraw-key-01", "0712345678", 1_000);

        assertEquals(id, receipt.transactionId());
        verify(lifecycle).complete(requestId, true, "provider-1");
    }

    @Test void replayDoesNotCallProviderAndInvalidInputsAreRejected() {
        UUID id = UUID.randomUUID();
        when(lifecycle.createPending(eq(id), eq("withdraw-key-02"), eq("+254712345678"), eq(1_000L), anyString()))
                .thenReturn(new WithdrawalLifecycleService.Started(null, id, "WLT-OLD", TransactionStatus.PENDING, false));
        var service = new WithdrawalService(lifecycle, b2c, new WalletProperties());

        service.withdraw(id, "withdraw-key-02", "0712345678", 1_000);
        assertThrows(IllegalArgumentException.class, () -> service.withdraw(id, "short", "0712345678", 1_000));
        assertThrows(IllegalArgumentException.class, () -> service.withdraw(id, "withdraw-key-03", "0712345678", 101));
        assertThrows(IllegalArgumentException.class, () -> service.withdraw(id, "withdraw-key-04", "0712345678", 0));
        verifyNoInteractions(b2c);
    }
}
