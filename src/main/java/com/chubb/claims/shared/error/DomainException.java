package com.chubb.claims.shared.error;

import org.springframework.http.HttpStatus;

public abstract class DomainException extends RuntimeException {

    private final String typeSuffix;
    private final HttpStatus status;

    protected DomainException(String typeSuffix, HttpStatus status, String message) {
        super(message);
        this.typeSuffix = typeSuffix;
        this.status = status;
    }

    public String getTypeSuffix() {
        return typeSuffix;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
