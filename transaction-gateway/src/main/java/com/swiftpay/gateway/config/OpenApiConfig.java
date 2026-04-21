package com.swiftpay.gateway.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private String serverPort;

    @Bean
    public OpenAPI swiftPayOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SwiftPay Transaction Gateway API")
                        .description("Real-Time P2P Payment Ledger — Service A: Transaction Gateway")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("SwiftPay Engineering")
                                .email("engineering@swiftpay.io"))
                        .license(new License().name("Apache 2.0")))
                .servers(List.of(
                        new Server().url("http://localhost:" + serverPort).description("Local"),
                        new Server().url("http://transaction-gateway:8080").description("Docker")));
    }
}
