package com.swiftpay.analytics.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI analyticsOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("SwiftPay Analytics Worker API")
                .description("Service C: Real-time OLAP payment volume monitoring")
                .version("v1.0.0"))
            .servers(List.of(
                new Server().url("http://localhost:8082").description("Local"),
                new Server().url("http://analytics-worker:8082").description("Docker")));
    }
}
