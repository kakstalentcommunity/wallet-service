package com.kennedy.wallet.service;

import com.kennedy.wallet.domain.TransactionStatus;
import com.kennedy.wallet.repository.MpesaDepositRequestRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;

@Component
public class MpesaStatusJob {
    private final MpesaDepositRequestRepository requests;
    private final MpesaClient mpesa;
    private final DepositLifecycleService lifecycle;
    public MpesaStatusJob(MpesaDepositRequestRepository requests,MpesaClient mpesa,DepositLifecycleService lifecycle){this.requests=requests;this.mpesa=mpesa;this.lifecycle=lifecycle;}

    @Scheduled(fixedDelayString="${wallet.mpesa.status-check-delay:PT1M}")
    public void resolveStaleRequests(){
        Instant now=Instant.now();
        for(var request:requests.findByTransaction_StatusAndInitiatedAtBefore(TransactionStatus.PENDING,now.minusSeconds(120))){
            boolean expired=request.getInitiatedAt().isBefore(now.minusSeconds(600));
            if(expired){lifecycle.resolveStatus(request.getId(),null,true);continue;}
            if(request.getCheckoutRequestId()==null)continue;
            try{
                MpesaClient.StkQueryResponse response=mpesa.queryStkStatus(request.getCheckoutRequestId());
                Integer code=null;
                if(response!=null&&response.ResultCode()!=null)try{code=Integer.valueOf(response.ResultCode());}catch(NumberFormatException ignored){ }
                lifecycle.resolveStatus(request.getId(),code,false);
            }catch(RuntimeException ignored){lifecycle.resolveStatus(request.getId(),null,false);}
        }
    }
}
