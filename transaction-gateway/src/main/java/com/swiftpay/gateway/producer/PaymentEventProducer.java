package com.swiftpay.gateway.producer;

import com.swiftpay.common.constants.KafkaTopics;
import com.swiftpay.common.events.PaymentInitiatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendPaymentInitiated(PaymentInitiatedEvent event) {
        String key = event.getSenderId().toString(); // partition by sender for ordering
        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(KafkaTopics.PAYMENT_INITIATED, key, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to send PaymentInitiatedEvent for paymentId={}: {}",
                        event.getPaymentId(), ex.getMessage(), ex);
            } else {
                log.info("PaymentInitiatedEvent sent: paymentId={} partition={} offset={}",
                        event.getPaymentId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
