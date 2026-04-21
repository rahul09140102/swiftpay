package com.swiftpay.gateway;

import com.swiftpay.common.dto.PaymentRequestDTO;
import com.swiftpay.common.dto.PaymentResponseDTO;
import com.swiftpay.gateway.entity.Payment;
import com.swiftpay.gateway.exception.DuplicateTransactionException;
import com.swiftpay.gateway.exception.InsufficientFundsException;
import com.swiftpay.gateway.producer.PaymentEventProducer;
import com.swiftpay.gateway.repository.PaymentRepository;
import com.swiftpay.gateway.service.BalanceCacheService;
import com.swiftpay.gateway.service.IdempotencyService;
import com.swiftpay.gateway.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock PaymentRepository    paymentRepository;
    @Mock IdempotencyService   idempotencyService;
    @Mock BalanceCacheService  balanceCacheService;
    @Mock PaymentEventProducer eventProducer;

    @InjectMocks PaymentService paymentService;

    private PaymentRequestDTO validRequest;
    private UUID senderId, receiverId;
    private String transactionId;

    @BeforeEach
    void setUp() {
        senderId      = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
        receiverId    = UUID.fromString("550e8400-e29b-41d4-a716-446655440002");
        transactionId = UUID.randomUUID().toString();
        validRequest  = PaymentRequestDTO.builder()
                .senderId(senderId).receiverId(receiverId)
                .amount(new BigDecimal("100.00")).currency("USD")
                .build();
    }

    @Test
    @DisplayName("Happy path: initiates payment successfully")
    void initiatePayment_success() {
        when(idempotencyService.getStoredResponse(transactionId)).thenReturn(Optional.empty());
        when(idempotencyService.tryAcquire(anyString(), anyString())).thenReturn(true);
        when(balanceCacheService.getCachedBalance(senderId))
                .thenReturn(Optional.of(new BigDecimal("500.00")));

        Payment saved = Payment.builder()
                .id(UUID.randomUUID()).transactionId(transactionId)
                .senderId(senderId).receiverId(receiverId)
                .amount(new BigDecimal("100.00")).currency("USD")
                .status(Payment.PaymentStatus.PENDING).build();
        when(paymentRepository.save(any())).thenReturn(saved);

        PaymentResponseDTO response = paymentService.initiatePayment(transactionId, validRequest);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("PENDING");
        assertThat(response.getSenderId()).isEqualTo(senderId);
        verify(eventProducer, times(1)).sendPaymentInitiated(any());
        verify(idempotencyService, times(1)).storeResponse(eq(transactionId), any());
    }

    @Test
    @DisplayName("Idempotent request returns cached response")
    void initiatePayment_idempotent_returnsCached() {
        PaymentResponseDTO cached = PaymentResponseDTO.builder()
                .id(UUID.randomUUID()).transactionId(transactionId).status("PENDING").build();
        when(idempotencyService.getStoredResponse(transactionId)).thenReturn(Optional.of(cached));

        PaymentResponseDTO response = paymentService.initiatePayment(transactionId, validRequest);

        assertThat(response).isEqualTo(cached);
        verifyNoInteractions(paymentRepository, eventProducer);
    }

    @Test
    @DisplayName("Throws InsufficientFundsException when balance is too low")
    void initiatePayment_insufficientFunds_throws() {
        when(idempotencyService.getStoredResponse(transactionId)).thenReturn(Optional.empty());
        when(idempotencyService.tryAcquire(anyString(), anyString())).thenReturn(true);
        when(balanceCacheService.getCachedBalance(senderId))
                .thenReturn(Optional.of(new BigDecimal("50.00")));

        assertThatThrownBy(() -> paymentService.initiatePayment(transactionId, validRequest))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("insufficient funds");
        verifyNoInteractions(paymentRepository);
        verifyNoInteractions(eventProducer);
    }

    @Test
    @DisplayName("Throws DuplicateTransactionException when lock cannot be acquired")
    void initiatePayment_duplicateLock_throws() {
        when(idempotencyService.getStoredResponse(transactionId)).thenReturn(Optional.empty());
        when(idempotencyService.tryAcquire(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> paymentService.initiatePayment(transactionId, validRequest))
                .isInstanceOf(DuplicateTransactionException.class);
    }

    @Test
    @DisplayName("Releases idempotency lock on unexpected exception")
    void initiatePayment_releasesLockOnFailure() {
        when(idempotencyService.getStoredResponse(transactionId)).thenReturn(Optional.empty());
        when(idempotencyService.tryAcquire(anyString(), anyString())).thenReturn(true);
        when(balanceCacheService.getCachedBalance(senderId))
                .thenReturn(Optional.of(new BigDecimal("500.00")));
        when(paymentRepository.save(any())).thenThrow(new RuntimeException("DB error"));

        assertThatThrownBy(() -> paymentService.initiatePayment(transactionId, validRequest))
                .isInstanceOf(RuntimeException.class);
        verify(idempotencyService, times(1)).release(transactionId);
    }
}
