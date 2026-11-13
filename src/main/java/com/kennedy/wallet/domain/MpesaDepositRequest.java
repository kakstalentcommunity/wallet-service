package com.kennedy.wallet.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "mpesa_deposit_requests")
public class MpesaDepositRequest {
    @Id private UUID id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "transaction_id", nullable = false, unique = true)
    private WalletTransaction transaction;
    @Column(name = "checkout_request_id", unique = true, length = 80) private String checkoutRequestId;
    @Column(name = "merchant_request_id", length = 80) private String merchantRequestId;
    @Column(nullable = false, length = 13) private String phone;
    @Column(name = "amount_minor", nullable = false) private long amountMinor;
    @Column(name = "initiated_at", nullable = false) private Instant initiatedAt = Instant.now();
    @Column(name = "last_status_checked_at") private Instant lastStatusCheckedAt;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "raw_callback", columnDefinition = "jsonb") private JsonNode rawCallback;

    protected MpesaDepositRequest() { }
    public MpesaDepositRequest(WalletTransaction transaction, String phone, long amountMinor) {
        this.id = UUID.randomUUID(); this.transaction = transaction; this.phone = phone; this.amountMinor = amountMinor;
    }
    public UUID getId(){return id;} public WalletTransaction getTransaction(){return transaction;}
    public String getCheckoutRequestId(){return checkoutRequestId;} public String getMerchantRequestId(){return merchantRequestId;}
    public String getPhone(){return phone;} public long getAmountMinor(){return amountMinor;} public Instant getInitiatedAt(){return initiatedAt;}
    public Instant getLastStatusCheckedAt(){return lastStatusCheckedAt;} public JsonNode getRawCallback(){return rawCallback;}
    public void setProviderRequestIds(String checkout, String merchant){checkoutRequestId=checkout;merchantRequestId=merchant;}
    public void receivedCallback(JsonNode callback){rawCallback=callback;}
    public void statusChecked(Instant when){lastStatusCheckedAt=when;}
}
