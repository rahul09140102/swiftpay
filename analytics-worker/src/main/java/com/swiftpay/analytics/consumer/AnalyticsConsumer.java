package com.swiftpay.analytics.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftpay.analytics.entity.PaymentAnalytics;
import com.swiftpay.analytics.repository.AnalyticsRepository;
import com.swiftpay.common.constants.KafkaTopics;
import com.swiftpay.common.events.PaymentCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyticsConsumer {

    private final AnalyticsRepository analyticsRepository;
    private final ObjectMapper         objectMapper;

    @KafkaListener(
        topics           = KafkaTopics.PAYMENT_COMPLETED,
        groupId          = "analytics-worker-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void onPaymentCompleted(
            @Payload Map<String, Object> payload,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        try {
            PaymentCompletedEvent event = objectMapper.convertValue(payload, PaymentCompletedEvent.class);

            if (analyticsRepository.existsByPaymentId(event.getPaymentId())) {
                log.debug("Duplicate analytics event for paymentId={}, skipping", event.getPaymentId());
                ack.acknowledge();
                return;
            }

            analyticsRepository.save(PaymentAnalytics.builder()
                    .paymentId(event.getPaymentId())
                    .transactionId(event.getTransactionId())
                    .senderId(event.getSenderId())
                    .receiverId(event.getReceiverId())
                    .amount(event.getAmount())
                    .currency(event.getCurrency())
                    .senderBalanceAfter(event.getSenderBalanceAfter())
                    .receiverBalanceAfter(event.getReceiverBalanceAfter())
                    .completedAt(event.getCompletedAt())
                    .build());

            log.info("Analytics ingested paymentId={} amount={} {}", 
                    event.getPaymentId(), event.getAmount(), event.getCurrency());
            ack.acknowledge();

        } catch (Exception e) {
            log.error("Analytics ingestion failed partition={} offset={}: {}", partition, offset, e.getMessage(), e);
            throw new RuntimeException("Analytics processing failed", e);
        }
    }
}
