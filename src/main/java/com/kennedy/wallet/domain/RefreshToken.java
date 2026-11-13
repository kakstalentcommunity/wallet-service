package com.kennedy.wallet.domain;
import jakarta.persistence.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="refresh_tokens") public class RefreshToken {
 @Id private UUID id; @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="user_id",nullable=false) private User user; @Column(name="token_hash",nullable=false,unique=true,length=64) private String tokenHash; @Column(name="expires_at",nullable=false) private Instant expiresAt; @Column(name="revoked_at") private Instant revokedAt; @Column(name="replaced_by_hash",length=64) private String replacedByHash; @Column(name="created_at",nullable=false) private Instant createdAt=Instant.now();
 protected RefreshToken(){} public RefreshToken(User user,String hash,Instant expiry){id=UUID.randomUUID();this.user=user;tokenHash=hash;expiresAt=expiry;} public User getUser(){return user;} public Instant getExpiresAt(){return expiresAt;} public Instant getRevokedAt(){return revokedAt;} public void revoke(String replacement){revokedAt=Instant.now();replacedByHash=replacement;}
}
