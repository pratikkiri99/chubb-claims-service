package com.chubb.claims.claim;

import com.chubb.claims.shared.domain.Market;
import com.chubb.claims.staff.Staff;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClaimRepository extends JpaRepository<Claim, UUID> {

    Optional<Claim> findByClaimNumber(String claimNumber);

    @EntityGraph(attributePaths = {"policy", "assignedStaff", "communications", "communications.staff"})
    @Query("select c from Claim c where c.claimNumber = :claimNumber")
    Optional<Claim> findDetailedByClaimNumber(@Param("claimNumber") String claimNumber);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Claim c
               set c.assignedStaff = :staff,
                   c.status = :inProgress,
                   c.assignedAt = :at,
                   c.version = c.version + 1,
                   c.updatedAt = :at
             where c.claimNumber = :claimNumber
               and c.status = :open
               and c.assignedStaff is null
            """)
    int assignIfOpen(
            @Param("claimNumber") String claimNumber,
            @Param("staff") Staff staff,
            @Param("inProgress") ClaimStatus inProgress,
            @Param("open") ClaimStatus open,
            @Param("at") Instant at);

    List<Claim> findByMarketAndStatusAndAssignedStaffIsNullOrderByCreatedAtDesc(
            Market market, ClaimStatus status);

    @Query("""
            select c.market as market, c.coverageType as coverageType,
                   coalesce(sum(c.reserveAmount), 0) as totalReserve,
                   count(c) as claimCount
            from Claim c
            where c.status in :statuses
              and (:market is null or c.market = :market)
            group by c.market, c.coverageType
            """)
    List<ExposureProjection> aggregateExposure(
            @Param("statuses") Collection<ClaimStatus> statuses,
            @Param("market") Market market);

    List<Claim> findByAssignedStaffAndStatusNotIn(Staff staff, Collection<ClaimStatus> statuses);

    long countByMarketAndStatusAndAssignedStaffIsNull(Market market, ClaimStatus status);

    List<Claim> findByMarketAndDecidedAtGreaterThanEqual(Market market, Instant cutoff);

    List<Claim> findByAssignedStaffTeamAndMarketAndDecidedAtGreaterThanEqual(
            String team, Market market, Instant cutoff);

    List<Claim> findByAssignedStaffTeamAndMarketAndStatusNotIn(
            String team, Market market, Collection<ClaimStatus> terminal);

    List<Claim> findByMarketAndStatusNotIn(Market market, Collection<ClaimStatus> terminal);
}
