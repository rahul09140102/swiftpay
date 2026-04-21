package com.swiftpay.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payment response payload")
public class PaymentResponseDTO {

    @Schema(description = "Unique payment identifier")
    private UUID id;

    @JsonProperty("transaction_id")
    @Schema(description = "Client-supplied idempotency key")
    private String transactionId;

    @JsonProperty("sender_id")
    private UUID senderId;

    @JsonProperty("receiver_id")
    private UUID receiverId;

    private BigDecimal amount;
    private String currency;
    private String status;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("updated_at")
    private Instant updatedAt;

    private String message;
}
