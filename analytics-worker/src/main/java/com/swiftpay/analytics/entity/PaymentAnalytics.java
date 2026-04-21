package com.swiftpay.analytics.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_analytics",
       indexes = {
           @Index(name = "idx_analytics_sender",    columnList = "sender_id"),
           @Index(name = "idx_analytics_receiver",  columnList = "receiver_id"),
           @Index(name = "idx_analytics_completed", columnList = "completed_at DESC"),
           @Index(name = "idx_analytics_currency",  columnList = "currency")
       })
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PaymentAnalytics {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "payment_id",     nullable = false, unique = true)
    private UUID paymentId;

    @Column(name = "transaction_id", nullable = false, length = 255)
    private String transactionId;

    @Column(name = "sender_id",   nullable = false)  private UUID   senderId;
    @Column(name = "receiver_id", nullable = false)  private UUID   receiverId;
    @Column(nullable = false, precision = 19, scale = 4) private BigDecimal amount;
    @Column(nullable = false, length = 3)             private String currency;

    @Column(name = "sender_balance_after",   precision = 19, scale = 4) private BigDecimal senderBalanceAfter;
    @Column(name = "receiver_balance_after", precision = 19, scale = 4) private BigDecimal receiverBalanceAfter;

    @Column(name = "completed_at") private Instant completedAt;

    @CreationTimestamp
    @Column(name = "ingested_at", updatable = false) private Instant ingestedAt;
}
