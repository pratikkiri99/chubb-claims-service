package com.chubb.claims.shared.error;

import org.springframework.http.HttpStatus;

public class PolicyNotFoundException extends DomainException {

    public PolicyNotFoundException(String policyNumber) {
        super("policy-not-found", HttpStatus.NOT_FOUND, "Policy not found: " + policyNumber);
    }
}
