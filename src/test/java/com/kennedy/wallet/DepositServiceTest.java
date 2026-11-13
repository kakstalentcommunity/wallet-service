package com.kennedy.wallet;

import com.kennedy.wallet.config.WalletProperties;
import com.kennedy.wallet.domain.TransactionStatus;
import com.kennedy.wallet.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DepositServiceTest {
    @Mock DepositLifecycleService lifecycle;
    @Mock MpesaClient mpesa;

    @Test void startsProviderPushForNewPendingDeposit() {
        UUID actor = UUID.randomUUID(), requestId = UUID.randomUUID(), transactionId = UUID.randomUUID();
        var pending = new DepositLifecycleService.Started(requestId, transactionId, "DEP-1", TransactionStatus.PENDING, null, null, true);
        when(lifecycle.createPending(eq(actor), eq("deposit-key-01"), eq("+254712345678"), eq(1_200L), anyString())).thenReturn(pending);
        when(mpesa.startStkPush("+254712345678", 1_200, "DEP-1"))
                .thenReturn(new MpesaClient.StkResponse("merchant", "checkout", "0", "accepted", "Check phone"));
        when(lifecycle.attachProviderResponse(requestId, new MpesaClient.StkResponse("merchant", "checkout", "0", "accepted", "Check phone")))
                .thenReturn(new DepositLifecycleService.Started(requestId, transactionId, "DEP-1", TransactionStatus.PENDING, "checkout", null, true));
        var service = new DepositService(lifecycle, mpesa, new WalletProperties());

        var receipt = service.start(actor, "deposit-key-01", "0712345678", 1_200);

        assertEquals("checkout", receipt.checkoutRequestId());
        assertEquals("Check phone", receipt.customerMessage());
        verify(mpesa).startStkPush("+254712345678", 1_200, "DEP-1");
    }

    @Test void replaySkipsProviderAndTimeoutKeepsRequestPending() {
        UUID actor = UUID.randomUUID(), requestId = UUID.randomUUID(), transactionId = UUID.randomUUID();
        var replay = new DepositLifecycleService.Started(requestId, transactionId, "DEP-2", TransactionStatus.PENDING, "checkout-2", null, false);
        when(lifecycle.createPending(eq(actor), eq("deposit-key-02"), eq("+254712345678"), eq(1_200L), anyString())).thenReturn(replay);
        var service = new DepositService(lifecycle, mpesa, new WalletProperties());
        assertEquals("checkout-2", service.start(actor, "deposit-key-02", "0712345678", 1_200).checkoutRequestId());
        verifyNoInteractions(mpesa);

        var newRequest = new DepositLifecycleService.Started(requestId, transactionId, "DEP-3", TransactionStatus.PENDING, null, null, true);
        when(lifecycle.createPending(eq(actor), eq("deposit-key-03"), eq("+254712345678"), eq(1_200L), anyString())).thenReturn(newRequest);
        when(mpesa.startStkPush(anyString(), anyLong(), anyString())).thenThrow(new IllegalStateException("timeout"));
        assertEquals(TransactionStatus.PENDING, service.start(actor, "deposit-key-03", "0712345678", 1_200).status());
    }

    @Test void validatesIdempotencyAndWholeKesAmounts() {
        var service = new DepositService(lifecycle, mpesa, new WalletProperties());
        UUID actor = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> service.start(actor, "short", "0712345678", 1_200));
        assertThrows(IllegalArgumentException.class, () -> service.start(actor, "deposit-key-04", "0712345678", 101));
        assertThrows(IllegalArgumentException.class, () -> service.start(actor, "deposit-key-05", "bad", 1_200));
        verifyNoInteractions(lifecycle, mpesa);
    }
}
