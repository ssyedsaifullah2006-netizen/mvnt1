package com.dispatch.exception;

public class InvalidEmergencyRequestException extends RuntimeException {
    public InvalidEmergencyRequestException(String message) {
        super(message);
    }
}
