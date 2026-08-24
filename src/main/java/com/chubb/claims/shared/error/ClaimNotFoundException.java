package com.chubb.claims.shared.error;

import org.springframework.http.HttpStatus;

public class ClaimNotFoundException extends DomainException {

    public ClaimNotFoundException(String claimNumber) {
        super("claim-not-found", HttpStatus.NOT_FOUND, "Claim not found: " + claimNumber);
    }
}
