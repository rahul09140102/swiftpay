package com.swiftpay.analytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication
@EnableKafka
public class AnalyticsWorkerApplication {
    public static void main(String[] args) {
        SpringApplication.run(AnalyticsWorkerApplication.class, args);
    }
}
