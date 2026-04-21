package com.swiftpay.analytics.controller;

import com.swiftpay.analytics.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Real-time OLAP payment volume monitoring")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/summary")
    @Operation(summary = "Last 24-hour transaction summary")
    public ResponseEntity<Map<String, Object>> summary() {
        return ResponseEntity.ok(analyticsService.getLast24hSummary());
    }

    @GetMapping("/volume-by-currency")
    @Operation(summary = "Total volume grouped by currency")
    public ResponseEntity<List<Map<String, Object>>> volumeByCurrency() {
        return ResponseEntity.ok(analyticsService.getVolumeByCurrency());
    }

    @GetMapping("/window")
    @Operation(summary = "Summary for a rolling time window (default 5 min)")
    public ResponseEntity<Map<String, Object>> window(
            @RequestParam(defaultValue = "5") int minutes) {
        return ResponseEntity.ok(analyticsService.getWindowSummary(minutes));
    }
}
