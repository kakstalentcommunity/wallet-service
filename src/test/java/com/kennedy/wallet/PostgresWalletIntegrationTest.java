package com.kennedy.wallet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kennedy.wallet.domain.*;
import com.kennedy.wallet.repository.*;
import com.kennedy.wallet.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class PostgresWalletIntegrationTest {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("wallet")
            .withUsername("wallet")
            .withPassword("wallet");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", postgres::getJdbcUrl);
        properties.add("spring.datasource.username", postgres::getUsername);
        properties.add("spring.datasource.password", postgres::getPassword);
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        properties.add("spring.flyway.enabled", () -> true);
        properties.add("wallet.fraud.transfers-per-minute", () -> 1000);
        properties.add("wallet.fraud.new-recipients-limit", () -> 1000);
        properties.add("logging.level.root", () -> "WARN");
    }

    @Autowired AuthService auth;
    @Autowired TransferService transfers;
    @Autowired DepositLifecycleService deposits;
    @Autowired LedgerService ledger;
    @Autowired UserRepository users;
    @Autowired AccountRepository accounts;
    @Autowired WalletTransactionRepository transactions;
    @Autowired LedgerEntryRepository entries;
    @Autowired MpesaDepositRequestRepository depositRequests;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void registerLoginRefreshAndLogoutWorkAgainstPostgres() {
        var user = auth.register("0712345101", "test-password-123");
        var initial = auth.login(user.getPhone(), "test-password-123");
        var rotated = auth.refresh(initial.refreshToken());

        assertNotEquals(initial.refreshToken(), rotated.refreshToken());
        auth.logout(rotated.refreshToken());
        assertThrows(IllegalArgumentException.class, () -> auth.refresh(rotated.refreshToken()));
    }

    @Test
    void transferSuccessReplayDifferentBodyAndInsufficientFundsAreEnforced() {
        var sender = auth.register("0712345102", "test-password-123");
        var recipient = auth.register("0712345103", "test-password-123");
        fund(sender, 10_000);

        var receipt = transfers.transfer(sender.getId(), "pg-transfer-0001", 500, recipient.getPhone());
        var replay = transfers.transfer(sender.getId(), "pg-transfer-0001", 500, recipient.getPhone());
        assertEquals(receipt.transactionId(), replay.transactionId());
        assertThrows(TransferService.IdempotencyConflictException.class,
                () -> transfers.transfer(sender.getId(), "pg-transfer-0001", 501, recipient.getPhone()));
        assertThrows(IllegalArgumentException.class,
                () -> transfers.transfer(sender.getId(), "pg-transfer-0002", 20_000, recipient.getPhone()));
    }

    @Test
    void duplicateMpesaCallbackCreditsTheWalletOnlyOnce() throws Exception {
        var user = auth.register("0712345104", "test-password-123");
        var pending = deposits.createPending(user.getId(), "pg-deposit-0001", user.getPhone(), 1_200, "a".repeat(64));
        deposits.attachProviderResponse(pending.requestId(), new MpesaClient.StkResponse(
                "merchant-1", "checkout-1", "0", "accepted", "sent"));
        var callback = objectMapper.readTree("""
                {"Body":{"stkCallback":{"CheckoutRequestID":"checkout-1","ResultCode":0,
                 "CallbackMetadata":{"Item":[{"Name":"Amount","Value":12},{"Name":"PhoneNumber","Value":254712345104}]}}}
                """);

        deposits.receiveCallback("checkout-1", callback);
        deposits.receiveCallback("checkout-1", callback);

        assertEquals(1_200, accounts.findByUser_Id(user.getId()).orElseThrow().getBalanceMinor());
        var transaction = transactions.findById(pending.transactionId()).orElseThrow();
        assertEquals(TransactionStatus.COMPLETED, transaction.getStatus());
        assertEquals(2, entries.findByTransaction_Id(transaction.getId()).size());
        assertTrue(depositRequests.findByTransaction_Id(transaction.getId()).orElseThrow().getRawCallback().isObject());
    }

    @Test
    void postgresTriggerRejectsLedgerEntryMutation() {
        var sender = auth.register("0712345105", "test-password-123");
        fund(sender, 2_000);
        var entryId = jdbc.queryForObject("select id from ledger_entries order by created_at desc limit 1", UUID.class);
        assertNotNull(entryId);
        assertThrows(RuntimeException.class,
                () -> jdbc.update("update ledger_entries set amount_minor = amount_minor + 1 where id = ?", entryId));
    }

    @Test
    void oneHundredParallelTransfersKeepBalancesNonnegativeAndLedgerBalanced() throws Exception {
        var sender = auth.register("0712345106", "test-password-123");
        var recipient = auth.register("0712345107", "test-password-123");
        fund(sender, 30_000);

        try (var executor = Executors.newFixedThreadPool(20)) {
            var tasks = new ArrayList<CompletableFuture<?>>();
            for (int i = 0; i < 100; i++) {
                int transfer = i;
                tasks.add(CompletableFuture.runAsync(() -> transfers.transfer(sender.getId(),
                        "parallel-key-%04d".formatted(transfer), 100, recipient.getPhone()), executor));
            }
            CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).get(90, TimeUnit.SECONDS);
        }

        assertEquals(10_000, accounts.findByUser_Id(sender.getId()).orElseThrow().getBalanceMinor());
        assertEquals(10_000, accounts.findByUser_Id(recipient.getId()).orElseThrow().getBalanceMinor());
        assertTrue(accounts.findByUser_Id(sender.getId()).orElseThrow().getBalanceMinor() >= 0);
        long[] totals = jdbc.queryForObject("select coalesce(sum(case when direction='DEBIT' then amount_minor else 0 end),0), " +
                "coalesce(sum(case when direction='CREDIT' then amount_minor else 0 end),0) from ledger_entries", (rs, row) ->
                new long[]{rs.getLong(1), rs.getLong(2)});
        assertEquals(totals[0], totals[1]);
    }

    private void fund(User user, long amount) {
        var wallet = accounts.findByUser_Id(user.getId()).orElseThrow();
        var clearing = accounts.findByCode("MPESA_CLEARING").orElseThrow();
        ledger.post(new LedgerService.TransactionDraft(TransactionType.ADJUSTMENT, TransactionStatus.COMPLETED, "KES", user.getId()),
                List.of(new LedgerService.LedgerLine(clearing.getId(), Direction.DEBIT, amount, "KES"),
                        new LedgerService.LedgerLine(wallet.getId(), Direction.CREDIT, amount, "KES")));
    }
}
