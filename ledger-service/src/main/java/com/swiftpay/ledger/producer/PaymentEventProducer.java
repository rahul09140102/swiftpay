package com.swiftpay.ledger.producer;

import com.swiftpay.common.constants.KafkaTopics;
import com.swiftpay.common.events.PaymentCompletedEvent;
import com.swiftpay.common.events.PaymentFailedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendPaymentCompleted(PaymentCompletedEvent event) {
        kafkaTemplate.send(KafkaTopics.PAYMENT_COMPLETED,
                           event.getSenderId().toString(), event)
            .whenComplete((r, ex) -> {
                if (ex != null) {
                    log.error("Failed to emit PaymentCompleted for paymentId={}: {}",
                            event.getPaymentId(), ex.getMessage());
                } else {
                    log.info("PaymentCompleted emitted for paymentId={}", event.getPaymentId());
                }
            });
    }

    public void sendPaymentFailed(PaymentFailedEvent event) {
        kafkaTemplate.send(KafkaTopics.PAYMENT_FAILED,
                           event.getSenderId().toString(), event)
            .whenComplete((r, ex) -> {
                if (ex != null) {
                    log.error("Failed to emit PaymentFailed for paymentId={}: {}",
                            event.getPaymentId(), ex.getMessage());
                } else {
                    log.info("PaymentFailed emitted for paymentId={} reason={}",
                            event.getPaymentId(), event.getFailureReason());
                }
            });
    }
}
