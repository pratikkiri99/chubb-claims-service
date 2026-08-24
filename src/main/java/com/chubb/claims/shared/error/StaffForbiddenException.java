package com.chubb.claims.shared.error;

import org.springframework.http.HttpStatus;

public class StaffForbiddenException extends DomainException {

    public StaffForbiddenException(String message) {
        super("staff-forbidden", HttpStatus.FORBIDDEN, message);
    }
}
