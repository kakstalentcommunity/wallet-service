package com.kennedy.wallet.service;

import com.kennedy.wallet.domain.*;
import com.kennedy.wallet.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;

@Service
public class AdminReversalService {
    private final WalletTransactionRepository transactions;private final LedgerEntryRepository entries;private final LedgerService ledger;private final AuditService audit;private final ApplicationEventPublisher events;
    public AdminReversalService(WalletTransactionRepository transactions,LedgerEntryRepository entries,LedgerService ledger,AuditService audit,ApplicationEventPublisher events){this.transactions=transactions;this.entries=entries;this.ledger=ledger;this.audit=audit;this.events=events;}

    /** Creates new opposite ledger entries and marks the completed source transaction as reversed. */
    @Transactional
    public Receipt reverse(UUID adminId,String key,UUID transactionId){
        if(key==null||key.length()<8||key.length()>80)throw new IllegalArgumentException("Idempotency-Key must contain 8 to 80 characters");
        String requestHash=hash(transactionId+":ADMIN_REVERSAL");
        Optional<WalletTransaction> prior=transactions.findByActorUserIdAndIdempotencyKey(adminId,key);
        if(prior.isPresent())return replay(prior.get(),requestHash);
        WalletTransaction original=transactions.findByIdForUpdate(transactionId).orElseThrow(()->new IllegalArgumentException("Transaction not found"));
        prior=transactions.findByActorUserIdAndIdempotencyKey(adminId,key);
        if(prior.isPresent())return replay(prior.get(),requestHash);
        if(original.getStatus()!=TransactionStatus.COMPLETED)throw new IllegalArgumentException("Only completed transactions can be reversed");
        List<LedgerEntry> source=entries.findByTransaction_Id(original.getId());
        if(source.size()<2)throw new IllegalStateException("Transaction ledger is incomplete");
        long amount=0;
        List<LedgerService.LedgerLine> opposite=new ArrayList<>();
        for(LedgerEntry entry:source){
            if(entry.getDirection()==Direction.DEBIT)amount=Math.addExact(amount,entry.getAmountMinor());
            opposite.add(new LedgerService.LedgerLine(entry.getAccount().getId(),
                    entry.getDirection()==Direction.DEBIT?Direction.CREDIT:Direction.DEBIT,entry.getAmountMinor(),entry.getCurrency()));
        }
        var draft=new LedgerService.TransactionDraft(TransactionType.REVERSAL,TransactionStatus.COMPLETED,original.getCurrency(),
                adminId,original.getAmountMinor()==null?amount:original.getAmountMinor(),original.getFeeMinor(),key,requestHash,original.getId());
        WalletTransaction reversal=ledger.post(draft,opposite);
        TransactionStateMachine.transition(original,TransactionStatus.REVERSED);
        audit.record(adminId,"TRANSACTION_REVERSED",Map.of("originalTransactionId",original.getId().toString(),"reversalTransactionId",reversal.getId().toString()));
        if(original.getActorUserId()!=null)events.publishEvent(new PaymentCompletedEvent(original.getActorUserId(),reversal.getReferenceCode(),amount,TransactionType.REVERSAL,true));
        return new Receipt(reversal.getId(),reversal.getReferenceCode(),original.getId(),original.getStatus());
    }

    private Receipt replay(WalletTransaction reversal,String requestHash){
        if(!MessageDigest.isEqual(reversal.getRequestHash().getBytes(StandardCharsets.US_ASCII),requestHash.getBytes(StandardCharsets.US_ASCII)))
            throw new TransferService.IdempotencyConflictException();
        return new Receipt(reversal.getId(),reversal.getReferenceCode(),reversal.getReversalOfId(),TransactionStatus.REVERSED);
    }
    private String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception ex){throw new IllegalStateException(ex);}}
    public record Receipt(UUID reversalTransactionId,String referenceCode,UUID originalTransactionId,TransactionStatus originalStatus){ }
}
