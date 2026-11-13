package com.kennedy.wallet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kennedy.wallet.domain.*;
import com.kennedy.wallet.repository.*;
import com.kennedy.wallet.service.DepositLifecycleService;
import com.kennedy.wallet.service.LedgerService;
import com.kennedy.wallet.service.AuditService;
import com.kennedy.wallet.service.PaymentCompletedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import java.util.UUID;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DepositLifecycleServiceTest {
    @Mock UserRepository users; @Mock AccountRepository accounts; @Mock WalletTransactionRepository transactions;
    @Mock MpesaDepositRequestRepository requests; @Mock LedgerService ledger;
    @Mock AuditService audit;@Mock ApplicationEventPublisher events;
    @Test void duplicateSuccessfulCallbackPostsLedgerOnlyOnce() throws Exception {
        UUID actor=UUID.randomUUID();
        WalletTransaction transaction=new WalletTransaction("DEP-1",TransactionType.DEPOSIT,TransactionStatus.PENDING,"KES",actor,1200L,0L,"deposit-key", "f".repeat(64));
        MpesaDepositRequest request=new MpesaDepositRequest(transaction,"+254712345678",1200);
        request.setProviderRequestIds("checkout-1","merchant-1");
        var wallet=mock(Account.class);var clearing=mock(Account.class);
        when(accounts.findByUser_Id(actor)).thenReturn(Optional.of(wallet));
        when(accounts.findByCode("MPESA_CLEARING")).thenReturn(Optional.of(clearing));
        when(requests.findByCheckoutRequestIdForUpdate("checkout-1")).thenReturn(Optional.of(request));
        when(wallet.getId()).thenReturn(UUID.randomUUID());when(clearing.getId()).thenReturn(UUID.randomUUID());
        doAnswer(invocation->{TransactionStateMachine.transition(transaction,TransactionStatus.COMPLETED);return transaction;}).when(ledger).postExisting(eq(transaction),anyList());
        var service=new DepositLifecycleService(users,accounts,transactions,requests,ledger,audit,events);
        var callback=new ObjectMapper().readTree("""
                {"Body":{"stkCallback":{"CheckoutRequestID":"checkout-1","ResultCode":0,"CallbackMetadata":{"Item":[{"Name":"Amount","Value":12},{"Name":"PhoneNumber","Value":254712345678}]}}}}
                """);

        service.receiveCallback("checkout-1",callback);
        service.receiveCallback("checkout-1",callback);

        verify(ledger,times(1)).postExisting(eq(transaction),anyList());
    }
}
