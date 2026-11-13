package com.kennedy.wallet.service;

import com.kennedy.wallet.config.WalletProperties;
import com.kennedy.wallet.domain.TransactionStatus;
import com.kennedy.wallet.domain.TransactionType;
import com.kennedy.wallet.domain.User;
import com.kennedy.wallet.repository.WalletTransactionRepository;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class FraudService {
    private final WalletTransactionRepository transactions;
    private final WalletProperties.Fraud rules;
    public FraudService(WalletTransactionRepository transactions,WalletProperties properties){this.transactions=transactions;this.rules=properties.getFraud();}

    /** Evaluates velocity, amount, and new-recipient signals before a transfer posts. */
    public Decision evaluateTransfer(UUID actor,UUID recipient,long amountMinor){
        Instant now=Instant.now();Instant minute=now.minus(1,ChronoUnit.MINUTES);Instant recipientWindow=now.minus(rules.getNewRecipientsWindowMinutes(),ChronoUnit.MINUTES);
        List<String> signals=new ArrayList<>();Outcome outcome=Outcome.ALLOW;
        long recent=transactions.countByActorUserIdAndTypeAndStatusAndCreatedAtGreaterThanEqual(actor,TransactionType.TRANSFER,TransactionStatus.COMPLETED,minute);
        if(recent>=rules.getTransfersPerMinute()){signals.add("TRANSFER_VELOCITY");outcome=Outcome.BLOCK;}
        if(amountMinor>rules.getSingleAmountFlagMinor()){signals.add("LARGE_AMOUNT");if(outcome==Outcome.ALLOW)outcome=Outcome.FLAG;}
        if(rules.getNewRecipientsLimit()>0&&transactions.countNewRecipientsSince(actor,recipientWindow)>=rules.getNewRecipientsLimit()
                &&transactions.countRecipientTransfersSince(actor,recipient,recipientWindow)==0){signals.add("NEW_RECIPIENT_VELOCITY");outcome=Outcome.BLOCK;}
        return new Decision(outcome,List.copyOf(signals));
    }

    public Decision evaluateFailedLogin(User user){
        if(user!=null&&user.getFailedLoginAttempts()>=rules.getRepeatedFailedLoginCount())return new Decision(Outcome.FLAG,List.of("REPEATED_FAILED_LOGIN"));
        return new Decision(Outcome.ALLOW,List.of());
    }
    public enum Outcome { ALLOW, FLAG, BLOCK }
    public record Decision(Outcome outcome,List<String> signals){ }
}
