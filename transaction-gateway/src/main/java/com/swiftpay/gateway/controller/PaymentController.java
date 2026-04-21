package com.swiftpay.gateway.controller;

import com.swiftpay.common.dto.ErrorResponseDTO;
import com.swiftpay.common.dto.PaymentRequestDTO;
import com.swiftpay.common.dto.PaymentResponseDTO;
import com.swiftpay.gateway.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "P2P Payment initiation and lookup")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @Operation(
        summary     = "Initiate a P2P payment",
        description = "Accepts a payment request, validates balance, persists a PENDING record, and emits a PaymentInitiated Kafka event."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Payment accepted",
            content = @Content(schema = @Schema(implementation = PaymentResponseDTO.class))),
        @ApiResponse(responseCode = "400", description = "Validation error",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
        @ApiResponse(responseCode = "409", description = "Duplicate transaction",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
        @ApiResponse(responseCode = "422", description = "Insufficient funds",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
        @ApiResponse(responseCode = "503", description = "Ledger service unavailable",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<PaymentResponseDTO> initiatePayment(
            @Parameter(description = "Idempotency key (UUID). Same key within 24h returns cached response.",
                       required = true, example = "550e8400-e29b-41d4-a716-446655440099")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PaymentRequestDTO request) {

        // Generate an idempotency key if the client did not supply one
        String transactionId = (idempotencyKey != null && !idempotencyKey.isBlank())
                ? idempotencyKey
                : UUID.randomUUID().toString();

        log.info("POST /v1/payments transactionId={} sender={} amount={} {}",
                transactionId, request.getSenderId(), request.getAmount(), request.getCurrency());

        PaymentResponseDTO response = paymentService.initiatePayment(transactionId, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/{paymentId}")
    @Operation(summary = "Retrieve a payment by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Payment found",
            content = @Content(schema = @Schema(implementation = PaymentResponseDTO.class))),
        @ApiResponse(responseCode = "404", description = "Payment not found",
            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<PaymentResponseDTO> getPayment(
            @PathVariable UUID paymentId) {
        return ResponseEntity.ok(paymentService.getPayment(paymentId));
    }
}
