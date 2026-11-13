package com.kennedy.wallet.repository;
import com.kennedy.wallet.domain.WithdrawalRequest;import jakarta.persistence.LockModeType;import org.springframework.data.jpa.repository.JpaRepository;import org.springframework.data.jpa.repository.Lock;import org.springframework.data.jpa.repository.Query;import org.springframework.data.repository.query.Param;import java.util.Optional;import java.util.UUID;
public interface WithdrawalRequestRepository extends JpaRepository<WithdrawalRequest,UUID>{
 Optional<WithdrawalRequest> findByTransaction_Id(UUID transactionId);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from WithdrawalRequest r where r.id=:id") Optional<WithdrawalRequest> findByIdForUpdate(@Param("id") UUID id);
}
