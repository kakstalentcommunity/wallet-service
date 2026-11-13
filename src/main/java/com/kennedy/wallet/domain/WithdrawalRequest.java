package com.kennedy.wallet.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="withdrawal_requests")
public class WithdrawalRequest {
    @Id private UUID id;
    @OneToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="transaction_id",nullable=false,unique=true) private WalletTransaction transaction;
    @Column(nullable=false,length=13) private String phone;
    @Column(name="amount_minor",nullable=false) private long amountMinor;
    @Column(name="provider_reference",length=80) private String providerReference;
    @Column(name="initiated_at",nullable=false) private Instant initiatedAt=Instant.now();
    protected WithdrawalRequest(){}
    public WithdrawalRequest(WalletTransaction transaction,String phone,long amountMinor){id=UUID.randomUUID();this.transaction=transaction;this.phone=phone;this.amountMinor=amountMinor;}
    public UUID getId(){return id;}public WalletTransaction getTransaction(){return transaction;}public String getPhone(){return phone;}public long getAmountMinor(){return amountMinor;}public String getProviderReference(){return providerReference;}public Instant getInitiatedAt(){return initiatedAt;}
    public void setProviderReference(String reference){providerReference=reference;}
}
