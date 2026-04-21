package com.swiftpay.gateway.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftpay.common.constants.KafkaTopics;
import com.swiftpay.common.events.PaymentCompletedEvent;
import com.swiftpay.common.events.PaymentFailedEvent;
import com.swiftpay.gateway.entity.Payment;
import com.swiftpay.gateway.repository.PaymentRepository;
import com.swiftpay.gateway.service.BalanceCacheService;
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
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentStatusConsumer {

    private final PaymentRepository paymentRepository;
    private final BalanceCacheService balanceCacheService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = KafkaTopics.PAYMENT_COMPLETED,
            groupId = "gateway-status-group",
            containerFactory = "statusListenerContainerFactory"
    )
    @Transactional
    public void onPaymentCompleted(@Payload Map<String, Object> payload,
                                   @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                   @Header(KafkaHeaders.OFFSET) long offset,
                                   Acknowledgment ack) {
        try {
            PaymentCompletedEvent event = objectMapper.convertValue(payload, PaymentCompletedEvent.class);
            log.info("PaymentCompleted received: paymentId={}", event.getPaymentId());

            paymentRepository.updateStatus(event.getPaymentId(),
                    Payment.PaymentStatus.COMPLETED, null);

            // Refresh balance cache with post-transaction balance
            balanceCacheService.updateBalance(event.getSenderId(), event.getSenderBalanceAfter());
            balanceCacheService.updateBalance(event.getReceiverId(), event.getReceiverBalanceAfter());

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error processing PaymentCompleted at partition={} offset={}: {}",
                    partition, offset, e.getMessage(), e);
            // Do NOT ack — let Kafka retry
        }
    }

    @KafkaListener(
            topics = KafkaTopics.PAYMENT_FAILED,
            groupId = "gateway-status-group",
            containerFactory = "statusListenerContainerFactory"
    )
    @Transactional
    public void onPaymentFailed(@Payload Map<String, Object> payload,
                                @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                @Header(KafkaHeaders.OFFSET) long offset,
                                Acknowledgment ack) {
        try {
            PaymentFailedEvent event = objectMapper.convertValue(payload, PaymentFailedEvent.class);
            log.info("PaymentFailed received: paymentId={} reason={}", event.getPaymentId(), event.getFailureReason());

            paymentRepository.updateStatus(event.getPaymentId(),
                    Payment.PaymentStatus.FAILED, event.getFailureReason());

            // Evict stale balance cache for sender to force re-fetch next time
            balanceCacheService.evictBalance(event.getSenderId());

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error processing PaymentFailed at partition={} offset={}: {}",
                    partition, offset, e.getMessage(), e);
        }
    }
}
