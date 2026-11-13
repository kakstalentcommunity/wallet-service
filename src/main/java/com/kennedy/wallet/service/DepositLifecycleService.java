package com.kennedy.wallet.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.kennedy.wallet.domain.*;
import com.kennedy.wallet.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;

@Service
public class DepositLifecycleService {
    private final UserRepository users;
    private final AccountRepository accounts;
    private final WalletTransactionRepository transactions;
    private final MpesaDepositRequestRepository requests;
    private final LedgerService ledger;
    private final AuditService audit;
    private final ApplicationEventPublisher events;

    public DepositLifecycleService(UserRepository users, AccountRepository accounts, WalletTransactionRepository transactions,
                                   MpesaDepositRequestRepository requests, LedgerService ledger,AuditService audit,ApplicationEventPublisher events) {
        this.users=users;this.accounts=accounts;this.transactions=transactions;this.requests=requests;this.ledger=ledger;this.audit=audit;this.events=events;
    }

    @Transactional
    public Started createPending(UUID userId, String key, String phone, long amountMinor, String requestHash) {
        User user=users.findById(userId).orElseThrow(()->new IllegalArgumentException("User unavailable"));
        Account wallet=accounts.findByUser_Id(userId).orElseThrow(()->new IllegalArgumentException("Account unavailable"));
        if(!user.isEnabled()||!wallet.isActive())throw new IllegalArgumentException("Account unavailable");
        if(accounts.findAllForUpdateByIdInOrderById(List.of(wallet.getId())).size()!=1)throw new IllegalArgumentException("Account unavailable");
        Optional<WalletTransaction> existing=transactions.findByActorUserIdAndIdempotencyKey(userId,key);
        if(existing.isPresent()){
            WalletTransaction tx=existing.get();
            if(!MessageDigestSupport.equal(tx.getRequestHash(),requestHash))throw new TransferService.IdempotencyConflictException();
            MpesaDepositRequest request=requests.findByTransaction_Id(tx.getId()).orElse(null);
            return Started.from(tx,request,false);
        }
        WalletTransaction tx=transactions.save(new WalletTransaction(reference(),TransactionType.DEPOSIT,TransactionStatus.PENDING,
                "KES",userId,amountMinor,0L,key,requestHash));
        MpesaDepositRequest request=requests.save(new MpesaDepositRequest(tx,phone,amountMinor));
        return Started.from(tx,request,true);
    }

    @Transactional
    public Started attachProviderResponse(UUID requestId, MpesaClient.StkResponse response) {
        MpesaDepositRequest request=requests.findById(requestId).orElseThrow();
        WalletTransaction tx=request.getTransaction();
        if(response==null)return Started.from(tx,request,false);
        if(!"0".equals(response.ResponseCode())){if(tx.getStatus()==TransactionStatus.PENDING){TransactionStateMachine.transition(tx,TransactionStatus.FAILED);recordOutcome(tx,false);}return Started.from(tx,request,false);}
        if(response.CheckoutRequestID()==null||response.CheckoutRequestID().isBlank()||response.MerchantRequestID()==null)
            return Started.from(tx,request,false);
        request.setProviderRequestIds(response.CheckoutRequestID(),response.MerchantRequestID());
        return Started.from(tx,request,false);
    }

    @Transactional
    public void receiveCallback(String checkoutId, JsonNode raw) {
        MpesaDepositRequest request=requests.findByCheckoutRequestIdForUpdate(checkoutId).orElse(null);
        if(request==null)return;
        request.receivedCallback(raw);
        WalletTransaction tx=request.getTransaction();
        if(tx.getStatus()!=TransactionStatus.PENDING)return;
        MpesaCallbackParser.CallbackData callback=MpesaCallbackParser.parse(raw);
        if(callback==null||!checkoutId.equals(callback.checkoutRequestId())||callback.amountKes()==null||callback.phone()==null)return;
        long callbackMinor;
        String normalizedPhone;
        try{callbackMinor=callback.amountKes().multiply(BigDecimal.valueOf(100)).longValueExact();normalizedPhone=PhoneNumbers.normalize(callback.phone());}
        catch(RuntimeException invalid){return;}
        if(callbackMinor!=request.getAmountMinor()||!request.getPhone().equals(normalizedPhone))return;
        settle(request,callback.resultCode());
    }

    @Transactional
    public void resolveStatus(UUID requestId, Integer resultCode, boolean expired) {
        MpesaDepositRequest request=requests.findById(requestId).orElse(null);
        if(request==null||request.getTransaction().getStatus()!=TransactionStatus.PENDING)return;
        request.statusChecked(Instant.now());
        if(resultCode!=null)settle(request,resultCode);
        else if(expired){TransactionStateMachine.transition(request.getTransaction(),TransactionStatus.FAILED);recordOutcome(request.getTransaction(),false);}
    }

    private void settle(MpesaDepositRequest request,int resultCode){
        WalletTransaction tx=request.getTransaction();
        if(tx.getStatus()!=TransactionStatus.PENDING)return;
        if(resultCode!=0){TransactionStateMachine.transition(tx,TransactionStatus.FAILED);recordOutcome(tx,false);return;}
        Account wallet=accounts.findByUser_Id(tx.getActorUserId()).orElseThrow();
        Account clearing=accounts.findByCode("MPESA_CLEARING").orElseThrow();
        ledger.postExisting(tx,List.of(new LedgerService.LedgerLine(clearing.getId(),Direction.DEBIT,request.getAmountMinor(),"KES"),
                new LedgerService.LedgerLine(wallet.getId(),Direction.CREDIT,request.getAmountMinor(),"KES")));
        recordOutcome(tx,true);
    }
    private void recordOutcome(WalletTransaction tx,boolean success){audit.record(tx.getActorUserId(),success?"MPESA_DEPOSIT_COMPLETED":"MPESA_DEPOSIT_FAILED",Map.of("transactionId",tx.getId().toString()));events.publishEvent(new PaymentCompletedEvent(tx.getActorUserId(),tx.getReferenceCode(),tx.getAmountMinor()==null?0:tx.getAmountMinor(),TransactionType.DEPOSIT,success));}
    private String reference(){return "DEP-"+UUID.randomUUID().toString().replace("-","").substring(0,16).toUpperCase(Locale.ROOT);}
    public record Started(UUID requestId,UUID transactionId,String referenceCode,TransactionStatus status,String checkoutRequestId,
                         String customerMessage,boolean created){
        static Started from(WalletTransaction tx,MpesaDepositRequest request,boolean created){return new Started(request==null?null:request.getId(),tx.getId(),tx.getReferenceCode(),tx.getStatus(),request==null?null:request.getCheckoutRequestId(),null,created);}
    }
    private static final class MessageDigestSupport { static boolean equal(String a,String b){return a!=null&&b!=null&&java.security.MessageDigest.isEqual(a.getBytes(java.nio.charset.StandardCharsets.US_ASCII),b.getBytes(java.nio.charset.StandardCharsets.US_ASCII));} }
}
