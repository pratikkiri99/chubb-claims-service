package com.chubb.claims.shared.error;

import org.springframework.http.HttpStatus;

public class StaffUnauthorizedException extends DomainException {

    public StaffUnauthorizedException() {
        super("staff-unauthorized", HttpStatus.UNAUTHORIZED, "Unknown or inactive staff");
    }
}
