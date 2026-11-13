package com.kennedy.wallet.repository;
import com.kennedy.wallet.domain.AuditLog;import org.springframework.data.jpa.repository.JpaRepository;import java.util.UUID;
public interface AuditLogRepository extends JpaRepository<AuditLog,UUID>{}
