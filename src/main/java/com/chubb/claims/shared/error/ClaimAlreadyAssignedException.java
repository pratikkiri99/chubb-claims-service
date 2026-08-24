package com.chubb.claims.shared.error;

import org.springframework.http.HttpStatus;

public class ClaimAlreadyAssignedException extends DomainException {

    public ClaimAlreadyAssignedException(String claimNumber) {
        super("claim-already-assigned", HttpStatus.CONFLICT, "Claim already assigned: " + claimNumber);
    }
}
