package com.swiftpay.ledger.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftpay.common.constants.KafkaTopics;
import com.swiftpay.common.events.PaymentInitiatedEvent;
import com.swiftpay.ledger.service.LedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final LedgerService ledgerService;
    private final ObjectMapper  objectMapper;

    /**
     * Consumes PaymentInitiated events.
     * Uses MANUAL_IMMEDIATE ack mode — only acknowledges after successful DB commit.
     * The KafkaConfig DefaultErrorHandler will retry with exponential back-off on failure.
     */
    @KafkaListener(
            topics            = KafkaTopics.PAYMENT_INITIATED,
            groupId           = "ledger-processor-group",
            containerFactory  = "kafkaListenerContainerFactory",
            concurrency       = "4"
    )
    public void onPaymentInitiated(
            @Payload Map<String, Object> payload,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment ack) {

        PaymentInitiatedEvent event = null;
        try {
            event = objectMapper.convertValue(payload, PaymentInitiatedEvent.class);
            log.info("Received PaymentInitiated: paymentId={} partition={} offset={}",
                    event.getPaymentId(), partition, offset);

            ledgerService.processPayment(event);
            ack.acknowledge();

            log.info("Acked PaymentInitiated: paymentId={}", event.getPaymentId());

        } catch (Exception e) {
            String paymentId = (event != null) ? event.getPaymentId().toString() : "unknown";
            log.error("Error processing PaymentInitiated paymentId={} partition={} offset={}: {}",
                    paymentId, partition, offset, e.getMessage(), e);
            // Do NOT ack — let the DefaultErrorHandler retry with backoff
            throw new RuntimeException("Ledger processing failed for payment " + paymentId, e);
        }
    }
}
