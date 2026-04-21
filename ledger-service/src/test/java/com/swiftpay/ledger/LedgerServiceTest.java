package com.swiftpay.ledger;

import com.swiftpay.common.events.PaymentInitiatedEvent;
import com.swiftpay.ledger.entity.Account;
import com.swiftpay.ledger.entity.LedgerEntry;
import com.swiftpay.ledger.producer.PaymentEventProducer;
import com.swiftpay.ledger.repository.AccountRepository;
import com.swiftpay.ledger.repository.LedgerEntryRepository;
import com.swiftpay.ledger.service.LedgerService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
class LedgerServiceTest {

    @Mock AccountRepository     accountRepository;
    @Mock LedgerEntryRepository ledgerEntryRepository;
    @Mock PaymentEventProducer  eventProducer;

    @InjectMocks LedgerService ledgerService;

    private UUID senderId, receiverId, paymentId;
    private Account senderAccount, receiverAccount;
    private PaymentInitiatedEvent event;

    @BeforeEach
    void setUp() {
        senderId   = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
        receiverId = UUID.fromString("550e8400-e29b-41d4-a716-446655440002");
        paymentId  = UUID.randomUUID();

        senderAccount = Account.builder()
                .id(UUID.randomUUID()).userId(senderId)
                .balance(new BigDecimal("500.00")).currency("USD").version(0L).build();
        receiverAccount = Account.builder()
                .id(UUID.randomUUID()).userId(receiverId)
                .balance(new BigDecimal("200.00")).currency("USD").version(0L).build();

        event = PaymentInitiatedEvent.builder()
                .paymentId(paymentId).transactionId("tx-001")
                .senderId(senderId).receiverId(receiverId)
                .amount(new BigDecimal("100.00")).currency("USD").build();
    }

    @Test
    @DisplayName("Successful debit/credit updates both accounts and emits PaymentCompleted")
    void processPayment_success() {
        when(ledgerEntryRepository.existsByTransactionIdAndEntryType(any(), any())).thenReturn(false);
        when(accountRepository.findByUserIdForUpdate(senderId)).thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByUserIdForUpdate(receiverId)).thenReturn(Optional.of(receiverAccount));
        when(accountRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(ledgerEntryRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ledgerService.processPayment(event);

        // Sender debited
        assertThat(senderAccount.getBalance()).isEqualByComparingTo("400.00");
        // Receiver credited
        assertThat(receiverAccount.getBalance()).isEqualByComparingTo("300.00");
        verify(accountRepository, times(2)).save(any());
        verify(ledgerEntryRepository, times(2)).save(any());
        verify(eventProducer, times(1)).sendPaymentCompleted(any());
        verify(eventProducer, never()).sendPaymentFailed(any());
    }

    @Test
    @DisplayName("Emits PaymentFailed and rolls back when sender has insufficient funds")
    void processPayment_insufficientFunds_emitsFailedEvent() {
        event = PaymentInitiatedEvent.builder()
                .paymentId(paymentId).transactionId("tx-002")
                .senderId(senderId).receiverId(receiverId)
                .amount(new BigDecimal("600.00")).currency("USD").build();

        when(ledgerEntryRepository.existsByTransactionIdAndEntryType(any(), any())).thenReturn(false);
        when(accountRepository.findByUserIdForUpdate(senderId)).thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByUserIdForUpdate(receiverId)).thenReturn(Optional.of(receiverAccount));

        ledgerService.processPayment(event);

        verify(eventProducer, times(1)).sendPaymentFailed(any());
        verify(eventProducer, never()).sendPaymentCompleted(any());
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("Skips duplicate transaction (idempotency guard)")
    void processPayment_duplicate_skipped() {
        when(ledgerEntryRepository.existsByTransactionIdAndEntryType(any(), eq(LedgerEntry.EntryType.DEBIT)))
                .thenReturn(true);

        ledgerService.processPayment(event);

        verifyNoInteractions(accountRepository);
        verifyNoInteractions(eventProducer);
    }

    @Test
    @DisplayName("Throws EntityNotFoundException when account missing")
    void processPayment_accountNotFound_throws() {
        when(ledgerEntryRepository.existsByTransactionIdAndEntryType(any(), any())).thenReturn(false);
        when(accountRepository.findByUserIdForUpdate(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ledgerService.processPayment(event))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("Correct ledger entry types are written")
    void processPayment_correctEntryTypes() {
        when(ledgerEntryRepository.existsByTransactionIdAndEntryType(any(), any())).thenReturn(false);
        when(accountRepository.findByUserIdForUpdate(senderId)).thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByUserIdForUpdate(receiverId)).thenReturn(Optional.of(receiverAccount));
        when(accountRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ArgumentCaptor<LedgerEntry> entryCaptor = ArgumentCaptor.forClass(LedgerEntry.class);
        when(ledgerEntryRepository.save(entryCaptor.capture())).thenAnswer(i -> i.getArgument(0));

        ledgerService.processPayment(event);

        var entries = entryCaptor.getAllValues();
        assertThat(entries).hasSize(2);
        assertThat(entries.stream().anyMatch(e -> e.getEntryType() == LedgerEntry.EntryType.DEBIT)).isTrue();
        assertThat(entries.stream().anyMatch(e -> e.getEntryType() == LedgerEntry.EntryType.CREDIT)).isTrue();
    }
}
