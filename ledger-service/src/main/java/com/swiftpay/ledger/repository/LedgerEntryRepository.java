package com.swiftpay.ledger.repository;

import com.swiftpay.ledger.entity.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    Page<LedgerEntry> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    List<LedgerEntry> findByTransactionIdOrderByCreatedAtAsc(String transactionId);

    boolean existsByTransactionIdAndEntryType(String transactionId, LedgerEntry.EntryType entryType);

    @Query("SELECT COUNT(e) FROM LedgerEntry e WHERE e.userId = :userId")
    long countByUserId(UUID userId);
}
