package com.kennedy.wallet.service;

import com.kennedy.wallet.config.WalletProperties;
import com.kennedy.wallet.domain.TransactionStatus;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class DepositService {
    private final DepositLifecycleService lifecycle;
    private final MpesaClient mpesa;
    private final WalletProperties.Transfer limits;
    public DepositService(DepositLifecycleService lifecycle, MpesaClient mpesa, WalletProperties properties) {
        this.lifecycle=lifecycle;this.mpesa=mpesa;this.limits=properties.getTransfer();
    }

    public Receipt start(UUID userId,String key,String phone,long amountMinor){
        if(key==null||key.length()<8||key.length()>80)throw new IllegalArgumentException("Idempotency-Key must contain 8 to 80 characters");
        if(amountMinor<=0||amountMinor%100!=0||amountMinor>limits.getMaximumAmountMinor())
            throw new IllegalArgumentException("Deposit amount must be positive, a whole KES amount, and within configured limits");
        String normalized=PhoneNumbers.normalize(phone);
        String fingerprint=hash(normalized+":"+amountMinor+":KES:MPESA_STK");
        DepositLifecycleService.Started started=lifecycle.createPending(userId,key,normalized,amountMinor,fingerprint);
        if(!started.created()||started.status()!=TransactionStatus.PENDING||started.checkoutRequestId()!=null)return receipt(started,null);
        try{
            MpesaClient.StkResponse response=mpesa.startStkPush(normalized,amountMinor,started.referenceCode());
            started=lifecycle.attachProviderResponse(started.requestId(),response);
            return receipt(started,response==null?null:response.CustomerMessage());
        }catch(RuntimeException failure){
            // The provider may have accepted a timed-out request. Leave it pending for callback/query/expiry resolution.
            return receipt(started,null);
        }
    }
    private Receipt receipt(DepositLifecycleService.Started started,String message){return new Receipt(started.transactionId(),started.referenceCode(),started.status(),started.checkoutRequestId(),message);}
    private String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception ex){throw new IllegalStateException(ex);}}
    public record Receipt(UUID transactionId,String referenceCode,TransactionStatus status,String checkoutRequestId,String customerMessage){ }
}
