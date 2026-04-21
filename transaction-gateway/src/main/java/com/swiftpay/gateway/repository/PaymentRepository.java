package com.swiftpay.gateway.repository;

import com.swiftpay.gateway.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByTransactionId(String transactionId);

    boolean existsByTransactionId(String transactionId);

    @Modifying
    @Query("UPDATE Payment p SET p.status = :status, p.failureReason = :failureReason WHERE p.id = :id")
    int updateStatus(UUID id, Payment.PaymentStatus status, String failureReason);
}
