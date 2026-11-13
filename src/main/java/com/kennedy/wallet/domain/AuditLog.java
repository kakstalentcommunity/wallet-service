package com.kennedy.wallet.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity @Table(name="audit_log")
public class AuditLog {
    @Id private UUID id;
    @Column(name="actor_user_id") private UUID actorUserId;
    @Column(nullable=false,length=64) private String action;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable=false,columnDefinition="jsonb") private Map<String,Object> detail;
    @Column(name="created_at",nullable=false) private Instant createdAt=Instant.now();
    protected AuditLog(){}
    public AuditLog(UUID actor,String action,Map<String,Object> detail){id=UUID.randomUUID();actorUserId=actor;this.action=action;this.detail=Map.copyOf(detail);}
    public UUID getId(){return id;}public UUID getActorUserId(){return actorUserId;}public String getAction(){return action;}public Map<String,Object> getDetail(){return detail;}public Instant getCreatedAt(){return createdAt;}
}
