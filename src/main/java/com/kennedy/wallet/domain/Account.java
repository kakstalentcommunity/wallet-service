package com.kennedy.wallet.domain;
import jakarta.persistence.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="accounts") public class Account {
 @Id private UUID id; @OneToOne(fetch=FetchType.LAZY) @JoinColumn(name="user_id",unique=true) private User user; @Column(name="balance_minor",nullable=false) private long balanceMinor; @Column(nullable=false,length=3) private String currency="KES"; @Column(nullable=false) private boolean active=true; @Column(name="created_at",nullable=false) private Instant createdAt=Instant.now();
 @Enumerated(EnumType.STRING) @Column(name="account_type",nullable=false,length=24) private AccountType accountType=AccountType.USER; @Column(unique=true,length=40) private String code;
 protected Account(){} public Account(User user){id=UUID.randomUUID();this.user=user;}
 public UUID getId(){return id;} public User getUser(){return user;} public long getBalanceMinor(){return balanceMinor;} public String getCurrency(){return currency;} public boolean isActive(){return active;} public AccountType getAccountType(){return accountType;} public String getCode(){return code;}
 public void applyBalanceDelta(long delta){balanceMinor=Math.addExact(balanceMinor,delta);if(accountType==AccountType.USER&&balanceMinor<0)throw new IllegalStateException("Wallet balance cannot be negative");}
}
