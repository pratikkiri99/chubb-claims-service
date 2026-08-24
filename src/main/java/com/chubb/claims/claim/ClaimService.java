package com.chubb.claims.claim;

import com.chubb.claims.claim.dto.SubmitClaimCommand;
import com.chubb.claims.event.ClaimEvent;
import com.chubb.claims.event.ClaimEventPublisher;
import com.chubb.claims.event.ClaimEventType;
import com.chubb.claims.policy.Policy;
import com.chubb.claims.policy.PolicyRepository;
import com.chubb.claims.policy.PolicyStatus;
import com.chubb.claims.shared.error.ClaimAlreadyAssignedException;
import com.chubb.claims.shared.error.ClaimNotFoundException;
import com.chubb.claims.shared.error.IllegalClaimStateException;
import com.chubb.claims.shared.error.PolicyNotActiveException;
import com.chubb.claims.shared.error.PolicyNotFoundException;
import com.chubb.claims.shared.error.ReserveLimitException;
import com.chubb.claims.shared.error.StaffForbiddenException;
import com.chubb.claims.shared.error.StaffUnauthorizedException;
import com.chubb.claims.staff.Staff;
import com.chubb.claims.staff.StaffRepository;
import com.chubb.claims.staff.StaffRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Validated
@Service
public class ClaimService {

    private final ClaimRepository claimRepository;
    private final PolicyRepository policyRepository;
    private final StaffRepository staffRepository;
    private final ClaimNumberSequence claimNumberSequence;
    private final ClaimEventPublisher eventPublisher;
    private final Clock clock;

    public ClaimService(
            ClaimRepository claimRepository,
            PolicyRepository policyRepository,
            StaffRepository staffRepository,
            ClaimNumberSequence claimNumberSequence,
            ClaimEventPublisher eventPublisher,
            Clock clock) {
        this.claimRepository = claimRepository;
        this.policyRepository = policyRepository;
        this.staffRepository = staffRepository;
        this.claimNumberSequence = claimNumberSequence;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public Claim submit(SubmitClaimCommand cmd) {
        Policy policy = policyRepository.findByPolicyNumberAndMarket(cmd.policyNumber(), cmd.market())
                .orElseThrow(() -> new PolicyNotFoundException(cmd.policyNumber()));
        if (policy.getStatus() != PolicyStatus.ACTIVE) {
            throw new PolicyNotActiveException(cmd.policyNumber());
        }
        if (cmd.incidentDate().isAfter(LocalDate.now(clock))) {
            throw new IllegalArgumentException("incidentDate must not be in the future");
        }
        if (cmd.claimedAmount().compareTo(policy.getSumInsured()) > 0) {
            throw new ReserveLimitException("claimedAmount exceeds sum insured");
        }
        String claimNumber = ClaimNumbers.format(policy.getMarket(), claimNumberSequence.nextValue());
        Claim claim = Claim.open(
                claimNumber,
                policy,
                cmd.claimantName(),
                cmd.claimantEmail(),
                cmd.claimantPhone(),
                cmd.incidentDate(),
                cmd.incidentLocation(),
                cmd.incidentDescription(),
                cmd.claimedAmount());
        claimRepository.save(claim);
        publish(ClaimEventType.CLAIM_SUBMITTED, claimNumber, Map.of("status", ClaimStatus.OPEN.name()));
        return claim;
    }

    @Transactional(readOnly = true)
    public Claim getByClaimNumber(String claimNumber) {
        return claimRepository.findByClaimNumber(claimNumber)
                .orElseThrow(() -> new ClaimNotFoundException(claimNumber));
    }

    @Transactional
    public Claim provideInformation(String claimNumber, String body) {
        Claim claim = getByClaimNumber(claimNumber);
        claim.provideInformation(body);
        publish(ClaimEventType.INFORMATION_PROVIDED, claimNumber, Map.of());
        return claim;
    }

    @Transactional
    public Claim assignToSelf(String claimNumber, UUID staffId) {
        Staff staff = requireActiveStaff(staffId);
        Claim claim = getByClaimNumber(claimNumber);
        assertSameMarket(claim, staff);
        if (claim.getStatus() != ClaimStatus.OPEN) {
            if (claim.getStatus() == ClaimStatus.SETTLED || claim.getStatus() == ClaimStatus.REJECTED) {
                throw new IllegalClaimStateException("Cannot assign a terminal claim");
            }
            throw new ClaimAlreadyAssignedException(claimNumber);
        }
        int updated = claimRepository.assignIfOpen(
                claimNumber, staff, ClaimStatus.IN_PROGRESS, ClaimStatus.OPEN, clock.instant());
        if (updated == 0) {
            throw new ClaimAlreadyAssignedException(claimNumber);
        }
        Claim assigned = getByClaimNumber(claimNumber);
        publish(ClaimEventType.CLAIM_ASSIGNED, claimNumber, Map.of("staffId", staffId.toString()));
        return assigned;
    }

    @Transactional
    public Claim requestInformation(String claimNumber, UUID staffId, String body) {
        Staff staff = requireActiveStaff(staffId);
        Claim claim = getByClaimNumber(claimNumber);
        assertActor(claim, staff);
        claim.requestInformation(staff, body);
        publish(ClaimEventType.INFORMATION_REQUESTED, claimNumber, Map.of());
        return claim;
    }

    @Transactional
    public Claim updateReserve(String claimNumber, UUID staffId, BigDecimal reserve) {
        Staff staff = requireActiveStaff(staffId);
        Claim claim = getByClaimNumber(claimNumber);
        assertActor(claim, staff);
        if (reserve.compareTo(BigDecimal.ZERO) < 0) {
            throw new ReserveLimitException("reserveAmount must be >= 0");
        }
        claim.updateReserve(reserve, claim.getPolicy().getSumInsured());
        return claim;
    }

    @Transactional
    public Claim settle(String claimNumber, UUID staffId, BigDecimal settlementAmount) {
        Staff staff = requireActiveStaff(staffId);
        Claim claim = getByClaimNumber(claimNumber);
        assertActor(claim, staff);
        claim.settle(settlementAmount, claim.getPolicy().getSumInsured(), clock.instant());
        publish(ClaimEventType.CLAIM_DECIDED, claimNumber, Map.of("decision", ClaimStatus.SETTLED.name()));
        return claim;
    }

    @Transactional
    public Claim reject(String claimNumber, UUID staffId, String reason) {
        Staff staff = requireActiveStaff(staffId);
        Claim claim = getByClaimNumber(claimNumber);
        assertActor(claim, staff);
        claim.reject(reason, clock.instant());
        publish(ClaimEventType.CLAIM_DECIDED, claimNumber, Map.of("decision", ClaimStatus.REJECTED.name()));
        return claim;
    }

    @Transactional(readOnly = true)
    public List<Claim> incomingQueue(UUID staffId) {
        Staff staff = requireActiveStaff(staffId);
        return claimRepository.findByMarketAndStatusAndAssignedStaffIsNullOrderByCreatedAtDesc(
                staff.getMarket(), ClaimStatus.OPEN);
    }

    @Transactional(readOnly = true)
    public Claim getForStaff(String claimNumber, UUID staffId) {
        Staff staff = requireActiveStaff(staffId);
        Claim claim = claimRepository.findDetailedByClaimNumber(claimNumber)
                .orElseGet(() -> getByClaimNumber(claimNumber));
        assertSameMarket(claim, staff);
        return claim;
    }

    private Staff requireActiveStaff(UUID staffId) {
        return staffRepository.findByIdAndActiveTrue(staffId)
                .orElseThrow(StaffUnauthorizedException::new);
    }

    private static void assertSameMarket(Claim claim, Staff staff) {
        if (claim.getMarket() != staff.getMarket()) {
            throw new StaffForbiddenException("Staff market does not match claim market");
        }
    }

    private static void assertActor(Claim claim, Staff staff) {
        assertSameMarket(claim, staff);
        if (staff.getRole() == StaffRole.MANAGER) {
            return;
        }
        if (claim.getAssignedStaff() == null || !staff.getId().equals(claim.getAssignedStaff().getId())) {
            throw new StaffForbiddenException("Officer is not the assignee");
        }
    }

    private void publish(ClaimEventType type, String claimNumber, Map<String, Object> payload) {
        eventPublisher.publish(new ClaimEvent(type, claimNumber, Instant.now(clock), payload));
    }
}
