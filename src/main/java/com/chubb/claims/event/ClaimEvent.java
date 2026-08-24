package com.chubb.claims.event;

import java.time.Instant;
import java.util.Map;

public record ClaimEvent(
        ClaimEventType type,
        String claimNumber,
        Instant occurredAt,
        Map<String, Object> payload
) {
}
