package com.kennedy.wallet.service;

import com.kennedy.wallet.domain.AuditLog;
import com.kennedy.wallet.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.util.Map;
import java.util.UUID;

@Service
public class AuditService {
    private final AuditLogRepository audit;
    public AuditService(AuditLogRepository audit){this.audit=audit;}
    /** Stores a security-relevant event without accepting credentials, tokens, phone numbers, or raw provider data. */
    @Transactional public void record(UUID actor,String action,Map<String,Object> detail){validate(action,detail);audit.save(new AuditLog(actor,action,detail));}
    @Transactional(propagation=Propagation.REQUIRES_NEW) public void recordIndependent(UUID actor,String action,Map<String,Object> detail){validate(action,detail);audit.save(new AuditLog(actor,action,detail));}
    private void validate(String action,Map<String,Object> detail){
        if(action==null||!action.matches("[A-Z0-9_]{2,64}"))throw new IllegalArgumentException("Invalid audit action");
        for(String key:detail.keySet()){String normalized=key.toLowerCase(java.util.Locale.ROOT);if(java.util.List.of("password","token","secret","phone","payload","credential").stream().anyMatch(normalized::contains))throw new IllegalArgumentException("Sensitive audit detail is not allowed");}
    }
}
