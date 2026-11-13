package com.kennedy.wallet.service;
import com.kennedy.wallet.domain.TransactionType;import java.util.UUID;
public record PaymentCompletedEvent(UUID userId,String referenceCode,long amountMinor,TransactionType type,boolean successful){ }
