package com.chubb.claims.claim.api;

import com.chubb.claims.claim.AuthorType;
import com.chubb.claims.claim.CommunicationKind;

import java.time.Instant;

public record CommunicationResponse(
        CommunicationKind kind,
        AuthorType authorType,
        String body,
        Instant createdAt
) {
}
