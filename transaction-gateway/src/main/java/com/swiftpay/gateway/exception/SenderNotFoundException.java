package com.swiftpay.gateway.exception;

public class SenderNotFoundException extends RuntimeException {
    public SenderNotFoundException(String message) { super(message); }
}
