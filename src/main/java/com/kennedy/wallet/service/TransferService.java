package com.kennedy.wallet.service;

import com.kennedy.wallet.config.WalletProperties;
import com.kennedy.wallet.domain.*;
import com.kennedy.wallet.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;

@Service
public class TransferService {
    private final UserRepository users;
    private final AccountRepository accounts;
    private final WalletTransactionRepository transactions;
    private final LedgerService ledger;
    private final FeeService fees;
    private final FraudService fraud;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final WalletProperties.Transfer config;

    public TransferService(UserRepository users, AccountRepository accounts, WalletTransactionRepository transactions,
                           LedgerService ledger, FeeService fees, WalletProperties properties,FraudService fraud,
                           AuditService audit,ApplicationEventPublisher events) {
        this.users = users; this.accounts = accounts; this.transactions = transactions; this.ledger = ledger;
        this.fees = fees; this.config = properties.getTransfer();this.fraud=fraud;this.audit=audit;this.events=events;
        if (config.getMinimumAmountMinor() <= 0 || config.getMaximumAmountMinor() < config.getMinimumAmountMinor()
                || config.getDailyLimitMinor() < config.getMinimumAmountMinor())
            throw new IllegalStateException("Transfer amount and daily limits are invalid");
    }

    /** Executes an idempotent wallet transfer, locking participant accounts in UUID order. */
    @Transactional
    public Receipt transfer(UUID senderId, String idempotencyKey, long amountMinor, String receiverPhone) {
        validateKey(idempotencyKey);
        if (amountMinor < config.getMinimumAmountMinor() || amountMinor > config.getMaximumAmountMinor())
            throw new IllegalArgumentException("Transfer amount is outside configured limits");
        String normalizedReceiver = PhoneNumbers.normalize(receiverPhone);
        String requestHash = requestHash(normalizedReceiver, amountMinor);
        Optional<WalletTransaction> previous = transactions.findByActorUserIdAndIdempotencyKey(senderId, idempotencyKey);
        if (previous.isPresent()) return replay(previous.get(), requestHash);

        User sender = users.findById(senderId).orElseThrow(TransferService::recipientUnavailable);
        if (!sender.isEnabled() || (sender.getLockedUntil() != null && sender.getLockedUntil().isAfter(Instant.now())))
            throw new IllegalArgumentException("Sender is not active");
        if (sender.getPhone().equals(normalizedReceiver)) throw recipientUnavailable();
        User receiver = users.findByPhone(normalizedReceiver).orElseThrow(TransferService::recipientUnavailable);
        if (!receiver.isEnabled()) throw recipientUnavailable();
        FraudService.Decision decision=fraud.evaluateTransfer(senderId,receiver.getId(),amountMinor);
        if(decision.outcome()==FraudService.Outcome.BLOCK){audit.recordIndependent(senderId,"TRANSFER_BLOCKED",Map.of("signals",String.join(",",decision.signals())));throw new FraudBlockedException();}
        if(decision.outcome()==FraudService.Outcome.FLAG)audit.recordIndependent(senderId,"FRAUD_FLAG",Map.of("signals",String.join(",",decision.signals())));
        Account senderAccount = accounts.findByUser_Id(senderId).orElseThrow(TransferService::recipientUnavailable);
        Account receiverAccount = accounts.findByUser_Id(receiver.getId()).orElseThrow(TransferService::recipientUnavailable);
        if (!senderAccount.isActive() || !receiverAccount.isActive()) throw recipientUnavailable();
        List<Account> locked = accounts.findAllForUpdateByIdInOrderById(new TreeSet<>(List.of(senderAccount.getId(), receiverAccount.getId())));
        if (locked.size() != 2) throw recipientUnavailable();
        Map<UUID, Account> lockedById = new HashMap<>(); locked.forEach(account -> lockedById.put(account.getId(), account));
        senderAccount = lockedById.get(senderAccount.getId()); receiverAccount = lockedById.get(receiverAccount.getId());
        if (!senderAccount.isActive() || !receiverAccount.isActive()) throw recipientUnavailable();

        // The sender account lock serializes retries from the same wallet; repeat the check after waiting for it.
        previous = transactions.findByActorUserIdAndIdempotencyKey(senderId, idempotencyKey);
        if (previous.isPresent()) return replay(previous.get(), requestHash);

        Instant dayStart = LocalDate.now(Clock.systemUTC()).atStartOfDay(ZoneOffset.UTC).toInstant();
        long alreadySent = transactions.sumCompletedTransfersSince(senderId, dayStart);
        if (Math.addExact(alreadySent, amountMinor) > config.getDailyLimitMinor())
            throw new IllegalArgumentException("Daily transfer limit exceeded");
        long fee = fees.feeFor(amountMinor);
        long total = Math.addExact(amountMinor, fee);
        if (senderAccount.getBalanceMinor() < total) throw new IllegalArgumentException("Insufficient funds");
        Account feeAccount = accounts.findByCode("FEE_REVENUE").orElseThrow(() -> new IllegalStateException("Fee account is not configured"));

        List<LedgerService.LedgerLine> lines = new ArrayList<>();
        lines.add(new LedgerService.LedgerLine(senderAccount.getId(), Direction.DEBIT, total, config.getCurrency()));
        lines.add(new LedgerService.LedgerLine(receiverAccount.getId(), Direction.CREDIT, amountMinor, config.getCurrency()));
        if (fee > 0) lines.add(new LedgerService.LedgerLine(feeAccount.getId(), Direction.CREDIT, fee, config.getCurrency()));
        var draft = new LedgerService.TransactionDraft(TransactionType.TRANSFER, TransactionStatus.COMPLETED,
                config.getCurrency(), senderId, amountMinor, fee, idempotencyKey, requestHash);
        WalletTransaction transaction = ledger.post(draft, lines);
        audit.record(senderId,"TRANSFER_COMPLETED",Map.of("transactionId",transaction.getId().toString(),"amountMinor",amountMinor));
        events.publishEvent(new PaymentCompletedEvent(senderId,transaction.getReferenceCode(),amountMinor,TransactionType.TRANSFER,true));
        return receipt(transaction);
    }

    private Receipt replay(WalletTransaction transaction, String requestHash) {
        if (!MessageDigest.isEqual(transaction.getRequestHash().getBytes(StandardCharsets.US_ASCII), requestHash.getBytes(StandardCharsets.US_ASCII)))
            throw new IdempotencyConflictException();
        return receipt(transaction);
    }
    private Receipt receipt(WalletTransaction transaction) {
        return new Receipt(transaction.getId(), transaction.getReferenceCode(), transaction.getStatus(),
                transaction.getAmountMinor(), transaction.getFeeMinor(), transaction.getCurrency(), transaction.getCreatedAt());
    }
    private String requestHash(String receiver, long amount) {
        try {
            String canonical = receiver + ":" + amount + ":" + config.getCurrency();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw new IllegalStateException("Could not fingerprint transfer request", ex); }
    }
    private void validateKey(String key) { if (key == null || key.length() < 8 || key.length() > 80) throw new IllegalArgumentException("Idempotency-Key must contain 8 to 80 characters"); }
    private static RecipientUnavailableException recipientUnavailable() { return new RecipientUnavailableException(); }
    public record Receipt(UUID transactionId, String referenceCode, TransactionStatus status, Long amountMinor,
                          Long feeMinor, String currency, Instant createdAt) { }
    public static class IdempotencyConflictException extends RuntimeException { public IdempotencyConflictException() { super("Idempotency key was used with a different request"); } }
    public static class RecipientUnavailableException extends RuntimeException { public RecipientUnavailableException() { super("Recipient is unavailable"); } }
    public static class FraudBlockedException extends RuntimeException { public FraudBlockedException(){super("Transfer blocked by risk controls");} }
}
