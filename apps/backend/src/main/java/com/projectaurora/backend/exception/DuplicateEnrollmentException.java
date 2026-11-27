package com.projectaurora.backend.exception;

public class DuplicateEnrollmentException extends RuntimeException {

    public DuplicateEnrollmentException(String message) {
        super(message);
    }
}