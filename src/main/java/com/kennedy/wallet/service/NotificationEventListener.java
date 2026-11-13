package com.kennedy.wallet.service;

import com.kennedy.wallet.domain.User;
import com.kennedy.wallet.repository.UserRepository;
import org.slf4j.Logger;import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;import org.springframework.stereotype.Component;import org.springframework.transaction.event.TransactionalEventListener;import org.springframework.transaction.event.TransactionPhase;

@Component public class NotificationEventListener {
    private static final Logger log=LoggerFactory.getLogger(NotificationEventListener.class);
    private final NotificationService notifications;private final UserRepository users;
    public NotificationEventListener(NotificationService notifications,UserRepository users){this.notifications=notifications;this.users=users;}
    @Async @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void afterPaymentCommit(PaymentCompletedEvent event){
        if(event.userId()==null)return;
        String message="Wallet "+event.type().name().toLowerCase(java.util.Locale.ROOT)+" "+(event.successful()?"completed":"failed")+". Ref: "+event.referenceCode();
        for(int attempt=1;attempt<=3;attempt++)try{User user=users.findById(event.userId()).orElse(null);if(user==null)return;notifications.send(user.getPhone(),message);return;}catch(RuntimeException failure){if(attempt==3)log.warn("Payment notification failed reference={} attempts={}",event.referenceCode(),attempt);else try{Thread.sleep(attempt*200L);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();return;}}
    }
}
