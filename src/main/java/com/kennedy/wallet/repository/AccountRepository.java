package com.kennedy.wallet.repository;
import com.kennedy.wallet.domain.Account; import org.springframework.data.jpa.repository.JpaRepository; import org.springframework.data.jpa.repository.Lock; import org.springframework.data.jpa.repository.Query; import org.springframework.data.repository.query.Param; import jakarta.persistence.LockModeType; import java.util.Collection; import java.util.List; import java.util.Optional; import java.util.UUID;
public interface AccountRepository extends JpaRepository<Account,UUID>{
 Optional<Account> findByUser_Id(UUID userId);
 Optional<Account> findByCode(String code);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select a from Account a where a.id in :ids order by a.id") List<Account> findAllForUpdateByIdInOrderById(@Param("ids") Collection<UUID> ids);
}
