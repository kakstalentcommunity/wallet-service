package com.kennedy.wallet;

import com.kennedy.wallet.domain.*;
import com.kennedy.wallet.repository.*;
import com.kennedy.wallet.service.*;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WithdrawalLifecycleServiceTest {
    @Mock UserRepository users;@Mock AccountRepository accounts;@Mock WalletTransactionRepository transactions;
    @Mock WithdrawalRequestRepository requests;@Mock LedgerEntryRepository entries;@Mock LedgerService ledger;
    @Mock AuditService audit;@Mock ApplicationEventPublisher events;

    @Test void failedB2cAutomaticallyPostsOppositeEntriesAndFailsOriginal(){
        UUID userId=UUID.randomUUID();WalletTransaction tx=new WalletTransaction("WLT-WITHDRAW",TransactionType.WITHDRAWAL,TransactionStatus.PENDING,"KES",userId,500L,0L,"withdraw-key","a".repeat(64));
        WithdrawalRequest request=new WithdrawalRequest(tx,"+254712345678",500);
        Account wallet=new Account(new User("+254712345678","hash"));Account suspense=new Account(new User("+254112345678","hash"));
        entriesFor(tx,wallet,suspense);
        when(requests.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        var service=new WithdrawalLifecycleService(users,accounts,transactions,requests,entries,ledger,audit,events);

        service.complete(request.getId(),false,"mock-failure");

        assertEquals(TransactionStatus.FAILED,tx.getStatus());
        verify(ledger).post(argThat(draft->draft.type()==TransactionType.REVERSAL&&draft.reversalOfId().equals(tx.getId())),argThat(lines->lines.size()==2&&lines.stream().allMatch(line->line.amountMinor()==500)));
    }

    @Test void successfulB2cCompletesWithoutReversal(){
        WalletTransaction tx=new WalletTransaction("WLT-WITHDRAW-2",TransactionType.WITHDRAWAL,TransactionStatus.PENDING,"KES",UUID.randomUUID(),500L,0L,"withdraw-key-2","b".repeat(64));
        WithdrawalRequest request=new WithdrawalRequest(tx,"+254712345678",500);
        when(requests.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        var service=new WithdrawalLifecycleService(users,accounts,transactions,requests,entries,ledger,audit,events);
        service.complete(request.getId(),true,"mock-success");
        assertEquals(TransactionStatus.COMPLETED,tx.getStatus());
        verifyNoInteractions(ledger);
    }

    private void entriesFor(WalletTransaction tx,Account debit,Account credit){
        when(entries.findByTransaction_Id(tx.getId())).thenReturn(List.of(
                new LedgerEntry(tx,debit,Direction.DEBIT,500,"KES"),new LedgerEntry(tx,credit,Direction.CREDIT,500,"KES")));
    }
}
