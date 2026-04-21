package com.swiftpay.analytics.repository;

import com.swiftpay.analytics.entity.PaymentAnalytics;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AnalyticsRepository extends JpaRepository<PaymentAnalytics, UUID> {

    boolean existsByPaymentId(UUID paymentId);

    @Query("SELECT SUM(a.amount) FROM PaymentAnalytics a WHERE a.completedAt BETWEEN :from AND :to")
    BigDecimal sumVolumeInRange(Instant from, Instant to);

    @Query("SELECT COUNT(a) FROM PaymentAnalytics a WHERE a.completedAt BETWEEN :from AND :to")
    long countInRange(Instant from, Instant to);

    @Query("SELECT a.currency, SUM(a.amount), COUNT(a) FROM PaymentAnalytics a GROUP BY a.currency")
    List<Object[]> volumeByCurrency();

    Page<PaymentAnalytics> findBySenderIdOrReceiverIdOrderByCompletedAtDesc(
            UUID senderId, UUID receiverId, Pageable pageable);
}
