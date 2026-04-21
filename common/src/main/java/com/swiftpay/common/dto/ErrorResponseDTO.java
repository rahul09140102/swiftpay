package com.swiftpay.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard error response")
public class ErrorResponseDTO {

    @Schema(description = "HTTP status code")
    private int status;

    @Schema(description = "Error type")
    private String error;

    @Schema(description = "Human-readable error message")
    private String message;

    @Schema(description = "Request path that triggered the error")
    private String path;

    @Schema(description = "Timestamp of the error")
    @Builder.Default
    private Instant timestamp = Instant.now();

    @Schema(description = "List of field-level validation errors")
    private List<FieldError> fieldErrors;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Schema(description = "Field-level validation error")
    public static class FieldError {
        private String field;
        private String message;
    }
}
