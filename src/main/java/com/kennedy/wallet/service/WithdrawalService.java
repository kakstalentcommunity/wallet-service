package com.kennedy.wallet.service;

import com.kennedy.wallet.config.WalletProperties;
import com.kennedy.wallet.domain.TransactionStatus;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class WithdrawalService {
    private final WithdrawalLifecycleService lifecycle;private final B2cClient b2c;private final WalletProperties.Transfer limits;
    public WithdrawalService(WithdrawalLifecycleService lifecycle,B2cClient b2c,WalletProperties properties){this.lifecycle=lifecycle;this.b2c=b2c;limits=properties.getTransfer();}
    public Receipt withdraw(UUID userId,String key,String phone,long amount){
        if(key==null||key.length()<8||key.length()>80)throw new IllegalArgumentException("Idempotency-Key must contain 8 to 80 characters");
        if(amount<limits.getMinimumAmountMinor()||amount>limits.getMaximumAmountMinor())throw new IllegalArgumentException("Withdrawal amount is outside configured limits");
        if(amount%100!=0)throw new IllegalArgumentException("M-Pesa withdrawal amount must be a whole KES amount");
        String normalized=PhoneNumbers.normalize(phone);
        String hash=fingerprint(normalized+":"+amount+":KES:WITHDRAWAL");
        var started=lifecycle.createPending(userId,key,normalized,amount,hash);
        if(started.created())b2c.submit(normalized,amount,started.referenceCode()).whenComplete((result,error)->
                lifecycle.complete(started.requestId(),error==null&&result!=null&&result.successful(),error==null&&result!=null?result.providerReference():null));
        return new Receipt(started.transactionId(),started.referenceCode(),started.status(),amount);
    }
    private String fingerprint(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception ex){throw new IllegalStateException(ex);}}
    public record Receipt(UUID transactionId,String referenceCode,TransactionStatus status,long amountMinor){ }
}
