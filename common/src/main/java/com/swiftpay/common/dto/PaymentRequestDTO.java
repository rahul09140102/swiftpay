package com.swiftpay.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payment request payload")
public class PaymentRequestDTO {

    @NotNull(message = "sender_id is required")
    @JsonProperty("sender_id")
    @Schema(description = "UUID of the sender", example = "550e8400-e29b-41d4-a716-446655440001")
    private UUID senderId;

    @NotNull(message = "receiver_id is required")
    @JsonProperty("receiver_id")
    @Schema(description = "UUID of the receiver", example = "550e8400-e29b-41d4-a716-446655440002")
    private UUID receiverId;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be greater than 0")
    @Digits(integer = 15, fraction = 4, message = "Invalid amount format")
    @Schema(description = "Amount to transfer", example = "100.00")
    private BigDecimal amount;

    @NotBlank(message = "currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be a 3-letter ISO code")
    @Schema(description = "ISO 4217 currency code", example = "USD")
    private String currency;
}
