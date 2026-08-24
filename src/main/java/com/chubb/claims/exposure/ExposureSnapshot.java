package com.chubb.claims.exposure;

import com.chubb.claims.shared.domain.CoverageType;
import com.chubb.claims.shared.domain.Market;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ExposureSnapshot(
        Instant asOf,
        BigDecimal totalOutstandingReserve,
        long outstandingClaimCount,
        List<Slice> byMarket,
        List<Slice> byCoverageType
) {

    public record Slice(String key, BigDecimal outstandingReserve, long claimCount) {
        static Slice of(Market market, BigDecimal reserve, long count) {
            return new Slice(market.name(), reserve, count);
        }

        static Slice of(CoverageType coverageType, BigDecimal reserve, long count) {
            return new Slice(coverageType.name(), reserve, count);
        }
    }
}
