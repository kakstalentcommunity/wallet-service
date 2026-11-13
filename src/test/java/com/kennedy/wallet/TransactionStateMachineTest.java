package com.kennedy.wallet;

import com.kennedy.wallet.domain.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TransactionStateMachineTest {
    @Test void allowsPendingToCompleteAndCompletedToReverse(){
        WalletTransaction transaction=new WalletTransaction("WLT-1",TransactionType.TRANSFER,TransactionStatus.PENDING,"KES",null);
        TransactionStateMachine.transition(transaction,TransactionStatus.COMPLETED);
        TransactionStateMachine.transition(transaction,TransactionStatus.REVERSED);
        assertEquals(TransactionStatus.REVERSED,transaction.getStatus());
    }
    @Test void rejectsIllegalTransitions(){
        WalletTransaction transaction=new WalletTransaction("WLT-2",TransactionType.TRANSFER,TransactionStatus.FAILED,"KES",null);
        assertThrows(IllegalStateException.class,()->TransactionStateMachine.transition(transaction,TransactionStatus.COMPLETED));
    }
}
