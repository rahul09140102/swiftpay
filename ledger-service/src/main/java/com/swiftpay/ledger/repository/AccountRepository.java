package com.swiftpay.ledger.repository;

import com.swiftpay.ledger.entity.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByUserId(UUID userId);

    /**
     * Pessimistic write lock for atomic balance updates.
     * Used as a fallback if optimistic lock conflicts are high.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.userId = :userId")
    Optional<Account> findByUserIdForUpdate(UUID userId);

    @Query("SELECT a.balance FROM Account a WHERE a.userId = :userId")
    Optional<java.math.BigDecimal> findBalanceByUserId(UUID userId);
}
