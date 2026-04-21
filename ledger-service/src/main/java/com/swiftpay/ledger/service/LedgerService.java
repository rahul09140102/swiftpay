package com.swiftpay.ledger.service;

import com.swiftpay.common.events.PaymentCompletedEvent;
import com.swiftpay.common.events.PaymentFailedEvent;
import com.swiftpay.common.events.PaymentInitiatedEvent;
import com.swiftpay.ledger.entity.Account;
import com.swiftpay.ledger.entity.LedgerEntry;
import com.swiftpay.ledger.producer.PaymentEventProducer;
import com.swiftpay.ledger.repository.AccountRepository;
import com.swiftpay.ledger.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final AccountRepository       accountRepository;
    private final LedgerEntryRepository   ledgerEntryRepository;
    private final PaymentEventProducer    eventProducer;

    /**
     * Atomically debits the sender and credits the receiver.
     * Runs under SERIALIZABLE isolation to prevent phantom reads / double-spend.
     *
     * On success  → emits PaymentCompletedEvent
     * On failure  → emits PaymentFailedEvent (no money moved)
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void processPayment(PaymentInitiatedEvent event) {
        String txId    = event.getTransactionId();
        log.info("Processing paymentId={} transactionId={}", event.getPaymentId(), txId);

        // ── Idempotency guard: skip if already processed ──────────────────
        if (ledgerEntryRepository.existsByTransactionIdAndEntryType(txId, LedgerEntry.EntryType.DEBIT)) {
            log.warn("Duplicate ledger processing detected for transactionId={}, skipping", txId);
            return;
        }

        // ── Load accounts with pessimistic lock (ordered to prevent deadlock) ─
        UUID senderId   = event.getSenderId();
        UUID receiverId = event.getReceiverId();

        // Always lock in consistent UUID order to prevent deadlock between concurrent txns
        boolean senderFirst = senderId.compareTo(receiverId) < 0;
        Account first  = loadForUpdate(senderFirst  ? senderId   : receiverId);
        Account second = loadForUpdate(!senderFirst ? senderId   : receiverId);
        Account sender   = senderFirst  ? first  : second;
        Account receiver = !senderFirst ? first  : second;

        // ── Validate sender balance ───────────────────────────────────────
        BigDecimal amount = event.getAmount();
        if (sender.getBalance().compareTo(amount) < 0) {
            String reason = String.format("Insufficient funds: balance=%s required=%s",
                    sender.getBalance(), amount);
            log.warn("Payment failed for paymentId={}: {}", event.getPaymentId(), reason);
            eventProducer.sendPaymentFailed(PaymentFailedEvent.builder()
                    .paymentId(event.getPaymentId())
                    .transactionId(txId)
                    .senderId(senderId)
                    .receiverId(receiverId)
                    .amount(amount)
                    .currency(event.getCurrency())
                    .failureReason(reason)
                    .build());
            return;
        }

        // ── Debit sender ──────────────────────────────────────────────────
        BigDecimal senderBefore  = sender.getBalance();
        BigDecimal senderAfter   = senderBefore.subtract(amount);
        sender.setBalance(senderAfter);
        accountRepository.save(sender);

        ledgerEntryRepository.save(LedgerEntry.builder()
                .transactionId(txId)
                .paymentId(event.getPaymentId())
                .accountId(sender.getId())
                .userId(senderId)
                .amount(amount)
                .entryType(LedgerEntry.EntryType.DEBIT)
                .balanceBefore(senderBefore)
                .balanceAfter(senderAfter)
                .currency(event.getCurrency())
                .build());

        // ── Credit receiver ───────────────────────────────────────────────
        BigDecimal receiverBefore = receiver.getBalance();
        BigDecimal receiverAfter  = receiverBefore.add(amount);
        receiver.setBalance(receiverAfter);
        accountRepository.save(receiver);

        ledgerEntryRepository.save(LedgerEntry.builder()
                .transactionId(txId)
                .paymentId(event.getPaymentId())
                .accountId(receiver.getId())
                .userId(receiverId)
                .amount(amount)
                .entryType(LedgerEntry.EntryType.CREDIT)
                .balanceBefore(receiverBefore)
                .balanceAfter(receiverAfter)
                .currency(event.getCurrency())
                .build());

        log.info("Ledger updated: sender {} {} → {}, receiver {} {} → {}",
                senderId, senderBefore, senderAfter,
                receiverId, receiverBefore, receiverAfter);

        // ── Emit completion event ─────────────────────────────────────────
        eventProducer.sendPaymentCompleted(PaymentCompletedEvent.builder()
                .paymentId(event.getPaymentId())
                .transactionId(txId)
                .senderId(senderId)
                .receiverId(receiverId)
                .amount(amount)
                .currency(event.getCurrency())
                .senderBalanceAfter(senderAfter)
                .receiverBalanceAfter(receiverAfter)
                .build());
    }

    private Account loadForUpdate(java.util.UUID userId) {
        return accountRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Account not found for userId=" + userId));
    }
}
