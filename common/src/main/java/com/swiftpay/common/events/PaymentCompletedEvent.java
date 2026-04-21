package com.swiftpay.common.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCompletedEvent {

    @JsonProperty("payment_id")
    private UUID paymentId;

    @JsonProperty("transaction_id")
    private String transactionId;

    @JsonProperty("sender_id")
    private UUID senderId;

    @JsonProperty("receiver_id")
    private UUID receiverId;

    private BigDecimal amount;
    private String currency;

    @JsonProperty("sender_balance_after")
    private BigDecimal senderBalanceAfter;

    @JsonProperty("receiver_balance_after")
    private BigDecimal receiverBalanceAfter;

    @JsonProperty("completed_at")
    @Builder.Default
    private Instant completedAt = Instant.now();

    @JsonProperty("event_type")
    @Builder.Default
    private String eventType = "PAYMENT_COMPLETED";
}
