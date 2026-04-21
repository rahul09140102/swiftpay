package com.swiftpay.ledger;

import com.swiftpay.common.constants.KafkaTopics;
import com.swiftpay.common.events.PaymentInitiatedEvent;
import com.swiftpay.ledger.repository.AccountRepository;
import com.swiftpay.ledger.repository.LedgerEntryRepository;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PaymentEventConsumerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("swiftpay_ledger")
            .withUsername("swiftpay")
            .withPassword("swiftpay_secret");

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",          postgres::getJdbcUrl);
        r.add("spring.datasource.username",      postgres::getUsername);
        r.add("spring.datasource.password",      postgres::getPassword);
        r.add("spring.kafka.bootstrap-servers",  kafka::getBootstrapServers);
    }

    @Autowired AccountRepository     accountRepository;
    @Autowired LedgerEntryRepository  ledgerEntryRepository;

    private KafkaTemplate<String, Object> testProducer;

    private static final UUID SENDER_ID   = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
    private static final UUID RECEIVER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440002");

    @BeforeEach
    void setUpProducer() {
        Map<String, Object> props = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class
        );
        testProducer = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }

    @Test
    @Order(1)
    @DisplayName("End-to-end: publish PaymentInitiated → ledger entries created")
    void endToEnd_paymentProcessed() {
        UUID paymentId = UUID.randomUUID();
        String txId    = "e2e-test-" + paymentId;

        PaymentInitiatedEvent event = PaymentInitiatedEvent.builder()
                .paymentId(paymentId)
                .transactionId(txId)
                .senderId(SENDER_ID)
                .receiverId(RECEIVER_ID)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .build();

        testProducer.send(KafkaTopics.PAYMENT_INITIATED, SENDER_ID.toString(), event);

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(ledgerEntryRepository.findByTransactionIdOrderByCreatedAtAsc(txId))
                    .hasSize(2);
        });

        BigDecimal senderBalance = accountRepository.findBalanceByUserId(SENDER_ID).orElseThrow();
        assertThat(senderBalance).isEqualByComparingTo("9900.00"); // 10000 - 100
    }
}
