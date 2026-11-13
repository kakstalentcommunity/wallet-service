package com.kennedy.wallet.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

/** The sole authority for legal transaction state transitions. */
public final class TransactionStateMachine {
    private static final Map<TransactionStatus, EnumSet<TransactionStatus>> ALLOWED = new EnumMap<>(TransactionStatus.class);
    static {
        ALLOWED.put(TransactionStatus.PENDING, EnumSet.of(TransactionStatus.COMPLETED, TransactionStatus.FAILED));
        ALLOWED.put(TransactionStatus.COMPLETED, EnumSet.of(TransactionStatus.REVERSED));
        ALLOWED.put(TransactionStatus.FAILED, EnumSet.noneOf(TransactionStatus.class));
        ALLOWED.put(TransactionStatus.REVERSED, EnumSet.noneOf(TransactionStatus.class));
    }
    private TransactionStateMachine() { }
    public static void transition(WalletTransaction transaction, TransactionStatus next) {
        if (!ALLOWED.get(transaction.getStatus()).contains(next))
            throw new IllegalStateException("Illegal transaction state transition");
        transaction.changeStatus(next);
    }
}
