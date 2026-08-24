package com.chubb.claims.shared.error;

import org.springframework.http.HttpStatus;

public class ReserveLimitException extends DomainException {

    public ReserveLimitException(String message) {
        super("reserve-limit", HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
