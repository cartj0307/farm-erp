package com.farmerp.common;

// Message is shown to the user as is
public class ValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ValidationException(String message) {
        super(message);
    }
}
