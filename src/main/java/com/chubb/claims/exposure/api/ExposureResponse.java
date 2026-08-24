package com.chubb.claims.exposure.api;

import com.chubb.claims.exposure.ExposureSnapshot;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ExposureResponse(
        Instant asOf,
        BigDecimal totalOutstandingReserve,
        long outstandingClaimCount,
        List<Slice> byMarket,
        List<Slice> byCoverageType
) {

    public record Slice(String key, BigDecimal outstandingReserve, long claimCount) {
    }

    public static ExposureResponse from(ExposureSnapshot snapshot) {
        return new ExposureResponse(
                snapshot.asOf(),
                snapshot.totalOutstandingReserve(),
                snapshot.outstandingClaimCount(),
                snapshot.byMarket().stream()
                        .map(s -> new Slice(s.key(), s.outstandingReserve(), s.claimCount()))
                        .toList(),
                snapshot.byCoverageType().stream()
                        .map(s -> new Slice(s.key(), s.outstandingReserve(), s.claimCount()))
                        .toList());
    }
}
