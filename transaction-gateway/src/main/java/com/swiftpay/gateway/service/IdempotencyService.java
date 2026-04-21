package com.swiftpay.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftpay.common.dto.PaymentResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final String KEY_PREFIX = "idempotency:";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Tries to acquire an idempotency lock for transactionId.
     * Returns true if the key is new (first-time request).
     * Returns false if the key already existed (duplicate request).
     */
    public boolean tryAcquire(String transactionId, String placeholder) {
        String key = KEY_PREFIX + transactionId;
        Boolean set = redisTemplate.opsForValue()
                .setIfAbsent(key, placeholder, TTL);
        return Boolean.TRUE.equals(set);
    }

    /**
     * Overwrite the placeholder with the real response JSON once the payment is saved.
     */
    public void storeResponse(String transactionId, PaymentResponseDTO response) {
        try {
            String key = KEY_PREFIX + transactionId;
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(key, json, TTL);
        } catch (Exception e) {
            log.warn("Failed to store idempotency response for {}: {}", transactionId, e.getMessage());
        }
    }

    /**
     * Retrieve a previously stored response, if any.
     */
    public Optional<PaymentResponseDTO> getStoredResponse(String transactionId) {
        String key = KEY_PREFIX + transactionId;
        String json = redisTemplate.opsForValue().get(key);
        if (json == null || json.equals("PROCESSING")) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, PaymentResponseDTO.class));
        } catch (Exception e) {
            log.warn("Failed to deserialize idempotency response for {}: {}", transactionId, e.getMessage());
            return Optional.empty();
        }
    }

    public void release(String transactionId) {
        redisTemplate.delete(KEY_PREFIX + transactionId);
    }
}
