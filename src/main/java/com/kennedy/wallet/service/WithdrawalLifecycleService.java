package com.kennedy.wallet.service;

import com.kennedy.wallet.domain.*;
import com.kennedy.wallet.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;

@Service
public class WithdrawalLifecycleService {
    private final UserRepository users;private final AccountRepository accounts;private final WalletTransactionRepository transactions;
    private final WithdrawalRequestRepository requests;private final LedgerEntryRepository entries;private final LedgerService ledger;
    private final AuditService audit;private final ApplicationEventPublisher events;
    public WithdrawalLifecycleService(UserRepository users,AccountRepository accounts,WalletTransactionRepository transactions,
            WithdrawalRequestRepository requests,LedgerEntryRepository entries,LedgerService ledger,AuditService audit,ApplicationEventPublisher events){this.users=users;this.accounts=accounts;this.transactions=transactions;this.requests=requests;this.entries=entries;this.ledger=ledger;this.audit=audit;this.events=events;}

    @Transactional
    public Started createPending(UUID userId,String key,String phone,long amount,String fingerprint){
        User user=users.findById(userId).orElseThrow(()->new IllegalArgumentException("Account unavailable"));
        Account wallet=accounts.findByUser_Id(userId).orElseThrow(()->new IllegalArgumentException("Account unavailable"));
        if(!user.isEnabled()||!wallet.isActive())throw new IllegalArgumentException("Account unavailable");
        List<Account> locked=accounts.findAllForUpdateByIdInOrderById(List.of(wallet.getId()));
        if(locked.size()!=1)throw new IllegalArgumentException("Account unavailable");
        wallet=locked.getFirst();
        Optional<WalletTransaction> existing=transactions.findByActorUserIdAndIdempotencyKey(userId,key);
        if(existing.isPresent())return replay(existing.get(),fingerprint);
        if(wallet.getBalanceMinor()<amount)throw new IllegalArgumentException("Insufficient funds");
        Account suspense=accounts.findByCode("SUSPENSE").orElseThrow(()->new IllegalStateException("Suspense account is not configured"));
        var draft=new LedgerService.TransactionDraft(TransactionType.WITHDRAWAL,TransactionStatus.PENDING,"KES",userId,amount,0L,key,fingerprint);
        WalletTransaction tx=ledger.post(draft,List.of(new LedgerService.LedgerLine(wallet.getId(),Direction.DEBIT,amount,"KES"),
                new LedgerService.LedgerLine(suspense.getId(),Direction.CREDIT,amount,"KES")));
        WithdrawalRequest request=requests.save(new WithdrawalRequest(tx,phone,amount));
        return Started.from(tx,request,true);
    }

    @Transactional
    public void complete(UUID requestId,boolean success,String providerReference){
        WithdrawalRequest request=requests.findByIdForUpdate(requestId).orElse(null);if(request==null)return;
        WalletTransaction original=request.getTransaction();if(original.getStatus()!=TransactionStatus.PENDING)return;
        request.setProviderReference(providerReference);
        if(success){TransactionStateMachine.transition(original,TransactionStatus.COMPLETED);recordOutcome(original,true);return;}
        List<LedgerService.LedgerLine> opposite=entries.findByTransaction_Id(original.getId()).stream().map(entry->
                new LedgerService.LedgerLine(entry.getAccount().getId(),entry.getDirection()==Direction.DEBIT?Direction.CREDIT:Direction.DEBIT,
                        entry.getAmountMinor(),entry.getCurrency())).toList();
        var reversalDraft=new LedgerService.TransactionDraft(TransactionType.REVERSAL,TransactionStatus.COMPLETED,
                original.getCurrency(),original.getActorUserId(),request.getAmountMinor(),0L,null,null,original.getId());
        ledger.post(reversalDraft,opposite);
        TransactionStateMachine.transition(original,TransactionStatus.FAILED);
        recordOutcome(original,false);
    }

    private Started replay(WalletTransaction tx,String fingerprint){
        if(!MessageDigest.isEqual(tx.getRequestHash().getBytes(StandardCharsets.US_ASCII),fingerprint.getBytes(StandardCharsets.US_ASCII)))
            throw new TransferService.IdempotencyConflictException();
        WithdrawalRequest request=requests.findByTransaction_Id(tx.getId()).orElse(null);return Started.from(tx,request,false);
    }
    public record Started(UUID requestId,UUID transactionId,String referenceCode,TransactionStatus status,boolean created){
        static Started from(WalletTransaction tx,WithdrawalRequest request,boolean created){return new Started(request==null?null:request.getId(),tx.getId(),tx.getReferenceCode(),tx.getStatus(),created);}
    }
    private void recordOutcome(WalletTransaction tx,boolean success){audit.record(tx.getActorUserId(),success?"WITHDRAWAL_COMPLETED":"WITHDRAWAL_FAILED",Map.of("transactionId",tx.getId().toString()));events.publishEvent(new PaymentCompletedEvent(tx.getActorUserId(),tx.getReferenceCode(),tx.getAmountMinor()==null?0:tx.getAmountMinor(),TransactionType.WITHDRAWAL,success));}
}
