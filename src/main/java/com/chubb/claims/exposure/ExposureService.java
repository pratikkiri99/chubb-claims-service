package com.chubb.claims.exposure;

import com.chubb.claims.claim.ClaimRepository;
import com.chubb.claims.claim.ClaimStatus;
import com.chubb.claims.claim.ExposureProjection;
import com.chubb.claims.shared.domain.CoverageType;
import com.chubb.claims.shared.domain.Market;
import com.chubb.claims.shared.error.StaffForbiddenException;
import com.chubb.claims.shared.error.StaffUnauthorizedException;
import com.chubb.claims.staff.Staff;
import com.chubb.claims.staff.StaffRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ExposureService {

    private static final Set<ClaimStatus> OUTSTANDING = EnumSet.of(
            ClaimStatus.OPEN, ClaimStatus.IN_PROGRESS, ClaimStatus.PENDING_INFORMATION);

    private final StaffRepository staffRepository;
    private final ClaimRepository claimRepository;
    private final Clock clock;

    public ExposureService(StaffRepository staffRepository, ClaimRepository claimRepository, Clock clock) {
        this.staffRepository = staffRepository;
        this.claimRepository = claimRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ExposureSnapshot get(UUID staffId, Optional<Market> marketFilter) {
        Staff staff = staffRepository.findByIdAndActiveTrue(staffId)
                .orElseThrow(StaffUnauthorizedException::new);
        Market market = marketFilter.orElse(staff.getMarket());
        if (market != staff.getMarket()) {
            throw new StaffForbiddenException("Staff may only view exposure for their market");
        }
        List<ExposureProjection> rows = claimRepository.aggregateExposure(OUTSTANDING, market);
        BigDecimal total = rows.stream()
                .map(ExposureProjection::getTotalReserve)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long count = rows.stream().mapToLong(ExposureProjection::getClaimCount).sum();

        Map<Market, List<ExposureProjection>> byMarket = rows.stream()
                .collect(Collectors.groupingBy(ExposureProjection::getMarket));
        Map<CoverageType, List<ExposureProjection>> byCoverage = rows.stream()
                .collect(Collectors.groupingBy(ExposureProjection::getCoverageType));

        return new ExposureSnapshot(
                clock.instant(),
                total,
                count,
                byMarket.entrySet().stream()
                        .map(e -> ExposureSnapshot.Slice.of(
                                e.getKey(),
                                sumReserve(e.getValue()),
                                e.getValue().stream().mapToLong(ExposureProjection::getClaimCount).sum()))
                        .toList(),
                byCoverage.entrySet().stream()
                        .map(e -> ExposureSnapshot.Slice.of(
                                e.getKey(),
                                sumReserve(e.getValue()),
                                e.getValue().stream().mapToLong(ExposureProjection::getClaimCount).sum()))
                        .toList());
    }

    private static BigDecimal sumReserve(List<ExposureProjection> rows) {
        return rows.stream()
                .map(ExposureProjection::getTotalReserve)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
