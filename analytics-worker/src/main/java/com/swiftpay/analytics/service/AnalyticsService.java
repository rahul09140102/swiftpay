package com.swiftpay.analytics.service;

import com.swiftpay.analytics.repository.AnalyticsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final AnalyticsRepository repo;

    @Transactional(readOnly = true)
    public Map<String, Object> getLast24hSummary() {
        Instant now  = Instant.now();
        Instant from = now.minus(24, ChronoUnit.HOURS);

        BigDecimal volume = repo.sumVolumeInRange(from, now);
        long count        = repo.countInRange(from, now);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("window", "last_24h");
        result.put("transaction_count", count);
        result.put("total_volume", volume != null ? volume : BigDecimal.ZERO);
        result.put("generated_at", now);
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getVolumeByCurrency() {
        List<Object[]> raw = repo.volumeByCurrency();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : raw) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("currency", row[0]);
            entry.put("total_volume", row[1]);
            entry.put("transaction_count", row[2]);
            result.add(entry);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getWindowSummary(int minutes) {
        Instant now  = Instant.now();
        Instant from = now.minus(minutes, ChronoUnit.MINUTES);
        BigDecimal volume = repo.sumVolumeInRange(from, now);
        long count        = repo.countInRange(from, now);
        double tps        = count > 0 ? (double) count / (minutes * 60) : 0.0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("window_minutes", minutes);
        result.put("transaction_count", count);
        result.put("total_volume", volume != null ? volume : BigDecimal.ZERO);
        result.put("avg_tps", String.format("%.2f", tps));
        result.put("generated_at", now);
        return result;
    }
}
