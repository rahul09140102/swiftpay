package com.swiftpay.ledger.controller;

import com.swiftpay.ledger.entity.Account;
import com.swiftpay.ledger.entity.LedgerEntry;
import com.swiftpay.ledger.repository.AccountRepository;
import com.swiftpay.ledger.repository.LedgerEntryRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Ledger", description = "Account balances and transaction history")
public class LedgerController {

    private final AccountRepository     accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    // ── GET /v1/transactions/{userId} ─────────────────────────────────
    @GetMapping("/v1/transactions/{userId}")
    @Operation(summary = "Transaction history for a user (paginated)")
    public ResponseEntity<Page<LedgerEntry>> getTransactionHistory(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<LedgerEntry> entries = ledgerEntryRepository
                .findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return ResponseEntity.ok(entries);
    }

    // ── GET /v1/accounts/{userId}/balance ─────────────────────────────
    @GetMapping("/v1/accounts/{userId}/balance")
    @Operation(summary = "Get current balance for a user (used by Gateway for balance check)")
    public ResponseEntity<BigDecimal> getBalance(@PathVariable UUID userId) {
        BigDecimal balance = accountRepository.findBalanceByUserId(userId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found: " + userId));
        return ResponseEntity.ok(balance);
    }

    // ── GET /v1/accounts/{userId} ─────────────────────────────────────
    @GetMapping("/v1/accounts/{userId}")
    @Operation(summary = "Get full account details for a user")
    public ResponseEntity<Account> getAccount(@PathVariable UUID userId) {
        Account account = accountRepository.findByUserId(userId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found: " + userId));
        return ResponseEntity.ok(account);
    }
}
