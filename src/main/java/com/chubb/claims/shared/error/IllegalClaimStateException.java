package com.chubb.claims.shared.error;

import org.springframework.http.HttpStatus;

public class IllegalClaimStateException extends DomainException {

    public IllegalClaimStateException(String message) {
        super("illegal-claim-state", HttpStatus.CONFLICT, message);
    }
}
