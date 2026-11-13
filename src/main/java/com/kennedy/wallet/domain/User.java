package com.kennedy.wallet.domain;
import jakarta.persistence.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="users") public class User {
 @Id private UUID id; @Column(nullable=false,unique=true,length=13) private String phone; @Column(name="password_hash",nullable=false) private String passwordHash;
 @Enumerated(EnumType.STRING) @Column(nullable=false) private Role role=Role.USER; @Column(nullable=false) private boolean enabled=true;
 @Column(name="failed_login_attempts",nullable=false) private int failedLoginAttempts; @Column(name="locked_until") private Instant lockedUntil; @Column(name="created_at",nullable=false) private Instant createdAt=Instant.now();
 protected User(){} public User(String phone,String passwordHash){id=UUID.randomUUID();this.phone=phone;this.passwordHash=passwordHash;}
 public UUID getId(){return id;} public String getPhone(){return phone;} public String getPasswordHash(){return passwordHash;} public Role getRole(){return role;} public boolean isEnabled(){return enabled;} public int getFailedLoginAttempts(){return failedLoginAttempts;} public Instant getLockedUntil(){return lockedUntil;}
 public void failedLogin(Instant until){failedLoginAttempts++;if(failedLoginAttempts>=5)lockedUntil=until;} public void successfulLogin(){failedLoginAttempts=0;lockedUntil=null;}
}
