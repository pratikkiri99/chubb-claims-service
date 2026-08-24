package com.chubb.claims.exposure;

import com.chubb.claims.claim.ClaimFixtures;
import com.chubb.claims.claim.ClaimRepository;
import com.chubb.claims.claim.ExposureProjection;
import com.chubb.claims.shared.domain.CoverageType;
import com.chubb.claims.shared.domain.Market;
import com.chubb.claims.shared.error.StaffForbiddenException;
import com.chubb.claims.staff.StaffRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExposureServiceTest {

    private static final Instant NOW = Instant.parse("2024-07-01T00:00:00Z");

    @Mock
    private StaffRepository staffRepository;
    @Mock
    private ClaimRepository claimRepository;

    private ExposureService exposureService;

    @BeforeEach
    void setUp() {
        exposureService = new ExposureService(
                staffRepository, claimRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void openReserveIsCounted() {
        when(staffRepository.findByIdAndActiveTrue(ClaimFixtures.OFFICER_ID))
                .thenReturn(Optional.of(ClaimFixtures.officer()));
        when(claimRepository.aggregateExposure(any(), eq(Market.AU)))
                .thenReturn(List.of(row(Market.AU, CoverageType.MOTOR, "1000.00", 1)));

        ExposureSnapshot snapshot = exposureService.get(ClaimFixtures.OFFICER_ID, Optional.empty());

        assertThat(snapshot.totalOutstandingReserve()).isEqualByComparingTo("1000.00");
        assertThat(snapshot.outstandingClaimCount()).isEqualTo(1);
        assertThat(snapshot.byMarket()).hasSize(1);
        assertThat(snapshot.byCoverageType()).hasSize(1);
    }

    @Test
    void settledIsExcludedFromQueryStatuses() {
        when(staffRepository.findByIdAndActiveTrue(ClaimFixtures.OFFICER_ID))
                .thenReturn(Optional.of(ClaimFixtures.officer()));
        when(claimRepository.aggregateExposure(any(), eq(Market.AU))).thenReturn(List.of());

        ExposureSnapshot snapshot = exposureService.get(ClaimFixtures.OFFICER_ID, Optional.empty());

        assertThat(snapshot.totalOutstandingReserve()).isEqualByComparingTo("0");
        assertThat(snapshot.outstandingClaimCount()).isZero();
    }

    @Test
    void otherMarketFilterIsForbidden() {
        when(staffRepository.findByIdAndActiveTrue(ClaimFixtures.OFFICER_ID))
                .thenReturn(Optional.of(ClaimFixtures.officer()));

        assertThatThrownBy(() -> exposureService.get(ClaimFixtures.OFFICER_ID, Optional.of(Market.HK)))
                .isInstanceOf(StaffForbiddenException.class);
    }

    private static ExposureProjection row(Market market, CoverageType coverageType, String reserve, long count) {
        return new ExposureProjection() {
            @Override
            public Market getMarket() {
                return market;
            }

            @Override
            public CoverageType getCoverageType() {
                return coverageType;
            }

            @Override
            public BigDecimal getTotalReserve() {
                return new BigDecimal(reserve);
            }

            @Override
            public long getClaimCount() {
                return count;
            }
        };
    }
}
