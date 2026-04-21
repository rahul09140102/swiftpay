package com.swiftpay.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftpay.common.dto.PaymentRequestDTO;
import com.swiftpay.gateway.service.PaymentService;
import com.swiftpay.common.dto.PaymentResponseDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentControllerIntegrationTest {

    @Autowired MockMvc     mvc;
    @Autowired ObjectMapper mapper;

    @MockBean PaymentService paymentService;

    @Test
    @DisplayName("POST /v1/payments returns 202 Accepted")
    void postPayment_returns202() throws Exception {
        PaymentRequestDTO request = PaymentRequestDTO.builder()
                .senderId(UUID.randomUUID()).receiverId(UUID.randomUUID())
                .amount(new BigDecimal("50.00")).currency("USD").build();

        PaymentResponseDTO response = PaymentResponseDTO.builder()
                .id(UUID.randomUUID()).status("PENDING")
                .amount(request.getAmount()).currency("USD").build();

        when(paymentService.initiatePayment(anyString(), any())).thenReturn(response);

        mvc.perform(post("/v1/payments")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request)))
           .andExpect(status().isAccepted())
           .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("POST /v1/payments returns 400 when body is invalid")
    void postPayment_invalidBody_returns400() throws Exception {
        String badBody = """
            { "sender_id": null, "receiver_id": null, "amount": -1, "currency": "US" }
            """;

        mvc.perform(post("/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(badBody))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.error").value("Validation Failed"));
    }
}
