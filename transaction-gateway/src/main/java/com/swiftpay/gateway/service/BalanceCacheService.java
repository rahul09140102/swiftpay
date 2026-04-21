package com.swiftpay.gateway.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BalanceCacheService {

    private static final String KEY_PREFIX = "balance:";
    private static final Duration CACHE_TTL = Duration.ofSeconds(30);

    private final StringRedisTemplate redisTemplate;

    public void updateBalance(UUID userId, BigDecimal balance) {
        String key = KEY_PREFIX + userId;
        redisTemplate.opsForValue().set(key, balance.toPlainString(), CACHE_TTL);
        log.debug("Updated cached balance for user {}: {}", userId, balance);
    }

    public Optional<BigDecimal> getCachedBalance(UUID userId) {
        String key = KEY_PREFIX + userId;
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BigDecimal(value));
        } catch (NumberFormatException e) {
            log.warn("Invalid cached balance for user {}: {}", userId, value);
            return Optional.empty();
        }
    }

    public void evictBalance(UUID userId) {
        redisTemplate.delete(KEY_PREFIX + userId);
    }
}
