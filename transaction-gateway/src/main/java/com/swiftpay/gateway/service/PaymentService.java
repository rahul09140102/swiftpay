package com.swiftpay.gateway.service;

import com.swiftpay.common.dto.PaymentRequestDTO;
import com.swiftpay.common.dto.PaymentResponseDTO;
import com.swiftpay.common.events.PaymentInitiatedEvent;
import com.swiftpay.gateway.entity.Payment;
import com.swiftpay.gateway.exception.DuplicateTransactionException;
import com.swiftpay.gateway.exception.InsufficientFundsException;
import com.swiftpay.gateway.exception.SenderNotFoundException;
import com.swiftpay.gateway.producer.PaymentEventProducer;
import com.swiftpay.gateway.repository.PaymentRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository   paymentRepository;
    private final IdempotencyService  idempotencyService;
    private final BalanceCacheService balanceCacheService;
    private final PaymentEventProducer eventProducer;
    private final RestTemplate        restTemplate;

    @Value("${swiftpay.ledger.url:http://ledger-service:8081}")
    private String ledgerServiceUrl;

    /**
     * Accepts a payment request:
     * 1. Idempotency check via Redis (24h TTL)
     * 2. Balance validation (Redis cache → Ledger REST fallback)
     * 3. Persist PENDING to PostgreSQL
     * 4. Emit PaymentInitiated to Kafka
     */
    @Transactional
    public PaymentResponseDTO initiatePayment(String transactionId, PaymentRequestDTO request) {

        // ── 1. Check for cached response (idempotent replay) ──────────────
        Optional<PaymentResponseDTO> cached = idempotencyService.getStoredResponse(transactionId);
        if (cached.isPresent()) {
            log.info("Idempotent replay for transactionId={}", transactionId);
            return cached.get();
        }

        // ── 2. Acquire idempotency lock ───────────────────────────────────
        boolean acquired = idempotencyService.tryAcquire(transactionId, "PROCESSING");
        if (!acquired) {
            cached = idempotencyService.getStoredResponse(transactionId);
            if (cached.isPresent()) return cached.get();
            throw new DuplicateTransactionException(
                    "Transaction " + transactionId + " is already being processed.");
        }

        try {
            // ── 3. Balance check ──────────────────────────────────────────
            BigDecimal senderBalance = resolveSenderBalance(request.getSenderId());
            if (senderBalance.compareTo(request.getAmount()) < 0) {
                throw new InsufficientFundsException(
                        "Sender " + request.getSenderId() + " has insufficient funds. " +
                        "Available: " + senderBalance + " " + request.getCurrency() +
                        ", Required: " + request.getAmount() + " " + request.getCurrency());
            }

            // ── 4. Persist PENDING ────────────────────────────────────────
            Payment payment = paymentRepository.save(Payment.builder()
                    .transactionId(transactionId)
                    .senderId(request.getSenderId())
                    .receiverId(request.getReceiverId())
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .status(Payment.PaymentStatus.PENDING)
                    .build());
            log.info("Payment persisted id={} status=PENDING", payment.getId());

            // ── 5. Emit Kafka event ───────────────────────────────────────
            eventProducer.sendPaymentInitiated(PaymentInitiatedEvent.builder()
                    .paymentId(payment.getId())
                    .transactionId(transactionId)
                    .senderId(request.getSenderId())
                    .receiverId(request.getReceiverId())
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .build());

            // ── 6. Cache response for idempotency ─────────────────────────
            PaymentResponseDTO response = toResponseDTO(payment);
            response.setMessage("Payment accepted and is being processed.");
            idempotencyService.storeResponse(transactionId, response);
            return response;

        } catch (Exception e) {
            idempotencyService.release(transactionId);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public PaymentResponseDTO getPayment(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .map(this::toResponseDTO)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found: " + paymentId));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private BigDecimal resolveSenderBalance(UUID senderId) {
        // 1. Redis cache (30s TTL)
        Optional<BigDecimal> cached = balanceCacheService.getCachedBalance(senderId);
        if (cached.isPresent()) {
            log.debug("Balance cache HIT for userId={}", senderId);
            return cached.get();
        }
        // 2. Ledger REST fallback
        log.debug("Balance cache MISS for userId={} — querying Ledger", senderId);
        try {
            String url = ledgerServiceUrl + "/v1/accounts/" + senderId + "/balance";
            BigDecimal balance = restTemplate.getForObject(url, BigDecimal.class);
            if (balance != null) {
                balanceCacheService.updateBalance(senderId, balance);
                return balance;
            }
        } catch (Exception e) {
            log.warn("Ledger service unreachable for userId={}: {}", senderId, e.getMessage());
        }
        throw new SenderNotFoundException(
                "Cannot verify balance for sender " + senderId + ". Ledger service unavailable.");
    }

    private PaymentResponseDTO toResponseDTO(Payment p) {
        return PaymentResponseDTO.builder()
                .id(p.getId())
                .transactionId(p.getTransactionId())
                .senderId(p.getSenderId())
                .receiverId(p.getReceiverId())
                .amount(p.getAmount())
                .currency(p.getCurrency())
                .status(p.getStatus().name())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
