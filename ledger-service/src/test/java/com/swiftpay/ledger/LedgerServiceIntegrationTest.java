package com.swiftpay.ledger;

import com.swiftpay.common.events.PaymentInitiatedEvent;
import com.swiftpay.ledger.entity.Account;
import com.swiftpay.ledger.repository.AccountRepository;
import com.swiftpay.ledger.repository.LedgerEntryRepository;
import com.swiftpay.ledger.service.LedgerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.swiftpay.ledger.producer.PaymentEventProducer;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

@SpringBootTest
@Testcontainers
class LedgerServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("swiftpay_ledger")
            .withUsername("swiftpay")
            .withPassword("swiftpay_secret");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @Autowired LedgerService        ledgerService;
    @Autowired AccountRepository    accountRepository;
    @Autowired LedgerEntryRepository ledgerEntryRepository;

    @MockBean PaymentEventProducer eventProducer;

    private UUID senderId, receiverId;

    @BeforeEach
    void setUp() {
        ledgerEntryRepository.deleteAll();
        accountRepository.deleteAll();

        senderId   = UUID.randomUUID();
        receiverId = UUID.randomUUID();

        accountRepository.save(Account.builder()
                .userId(senderId).balance(new BigDecimal("1000.00"))
                .currency("USD").version(0L).build());
        accountRepository.save(Account.builder()
                .userId(receiverId).balance(new BigDecimal("500.00"))
                .currency("USD").version(0L).build());

        doNothing().when(eventProducer).sendPaymentCompleted(any());
        doNothing().when(eventProducer).sendPaymentFailed(any());
    }

    @Test
    @DisplayName("Integration: balances are atomically updated in DB")
    void processPayment_updatesBalancesInDB() {
        PaymentInitiatedEvent event = PaymentInitiatedEvent.builder()
                .paymentId(UUID.randomUUID()).transactionId("integ-tx-001")
                .senderId(senderId).receiverId(receiverId)
                .amount(new BigDecimal("250.00")).currency("USD").build();

        ledgerService.processPayment(event);

        BigDecimal senderBalance   = accountRepository.findByUserId(senderId).get().getBalance();
        BigDecimal receiverBalance = accountRepository.findByUserId(receiverId).get().getBalance();

        assertThat(senderBalance).isEqualByComparingTo("750.00");
        assertThat(receiverBalance).isEqualByComparingTo("750.00");

        long entries = ledgerEntryRepository.countByUserId(senderId)
                + ledgerEntryRepository.countByUserId(receiverId);
        assertThat(entries).isEqualTo(2);
    }

    @Test
    @DisplayName("Integration: double-processing is idempotent (no duplicate entries)")
    void processPayment_idempotent_noDuplicates() {
        PaymentInitiatedEvent event = PaymentInitiatedEvent.builder()
                .paymentId(UUID.randomUUID()).transactionId("integ-tx-002")
                .senderId(senderId).receiverId(receiverId)
                .amount(new BigDecimal("100.00")).currency("USD").build();

        ledgerService.processPayment(event);
        ledgerService.processPayment(event); // second call must be a no-op

        BigDecimal senderBalance = accountRepository.findByUserId(senderId).get().getBalance();
        assertThat(senderBalance).isEqualByComparingTo("900.00"); // only debited once
    }
}
