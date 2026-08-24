package com.chubb.claims.shared.error;

import org.springframework.http.HttpStatus;

public class PolicyNotActiveException extends DomainException {

    public PolicyNotActiveException(String policyNumber) {
        super("policy-not-active", HttpStatus.UNPROCESSABLE_ENTITY, "Policy is not active: " + policyNumber);
    }
}
