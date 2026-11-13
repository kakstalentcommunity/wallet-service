package com.kennedy.wallet.repository;

import com.kennedy.wallet.domain.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MpesaDepositRequestRepository extends JpaRepository<MpesaDepositRequest, UUID> {
    Optional<MpesaDepositRequest> findByTransaction_Id(UUID transactionId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from MpesaDepositRequest r where r.checkoutRequestId = :checkoutId")
    Optional<MpesaDepositRequest> findByCheckoutRequestIdForUpdate(@Param("checkoutId") String checkoutId);
    List<MpesaDepositRequest> findByTransaction_StatusAndInitiatedAtBefore(TransactionStatus status, Instant before);
}
