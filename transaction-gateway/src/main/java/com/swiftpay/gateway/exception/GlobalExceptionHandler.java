package com.swiftpay.gateway.exception;

import com.swiftpay.common.dto.ErrorResponseDTO;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDTO> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest req) {

        List<ErrorResponseDTO.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(f -> new ErrorResponseDTO.FieldError(f.getField(), f.getDefaultMessage()))
                .toList();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ErrorResponseDTO.builder()
                        .status(400)
                        .error("Validation Failed")
                        .message("Request body has invalid fields")
                        .path(req.getRequestURI())
                        .fieldErrors(fieldErrors)
                        .build());
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponseDTO> handleInsufficientFunds(
            InsufficientFundsException ex, HttpServletRequest req) {
        log.warn("Insufficient funds: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
                ErrorResponseDTO.builder()
                        .status(422).error("Insufficient Funds")
                        .message(ex.getMessage()).path(req.getRequestURI()).build());
    }

    @ExceptionHandler(DuplicateTransactionException.class)
    public ResponseEntity<ErrorResponseDTO> handleDuplicate(
            DuplicateTransactionException ex, HttpServletRequest req) {
        log.warn("Duplicate transaction: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                ErrorResponseDTO.builder()
                        .status(409).error("Duplicate Transaction")
                        .message(ex.getMessage()).path(req.getRequestURI()).build());
    }

    @ExceptionHandler(SenderNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleSenderNotFound(
            SenderNotFoundException ex, HttpServletRequest req) {
        log.warn("Sender not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                ErrorResponseDTO.builder()
                        .status(503).error("Service Unavailable")
                        .message(ex.getMessage()).path(req.getRequestURI()).build());
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleEntityNotFound(
            EntityNotFoundException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ErrorResponseDTO.builder()
                        .status(404).error("Not Found")
                        .message(ex.getMessage()).path(req.getRequestURI()).build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleGeneric(Exception ex, HttpServletRequest req) {
        log.error("Unexpected error at {}: {}", req.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ErrorResponseDTO.builder()
                        .status(500).error("Internal Server Error")
                        .message("An unexpected error occurred. Please try again later.")
                        .path(req.getRequestURI()).build());
    }
}
