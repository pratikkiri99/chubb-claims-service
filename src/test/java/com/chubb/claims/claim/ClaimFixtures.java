package com.chubb.claims.claim;

import com.chubb.claims.policy.Policy;
import com.chubb.claims.policy.PolicyStatus;
import com.chubb.claims.shared.domain.CoverageType;
import com.chubb.claims.shared.domain.Market;
import com.chubb.claims.staff.Staff;
import com.chubb.claims.staff.StaffRole;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public final class ClaimFixtures {

    public static final UUID OFFICER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID OTHER_OFFICER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    public static final UUID MANAGER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    public static final UUID HK_OFFICER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private ClaimFixtures() {
    }

    public static Policy activeAuMotor() {
        Policy policy = new Policy();
        policy.setId(UUID.fromString("55555555-5555-5555-5555-555555555555"));
        policy.setPolicyNumber("POL-AU-MOTOR-001");
        policy.setMarket(Market.AU);
        policy.setCoverageType(CoverageType.MOTOR);
        policy.setHolderName("Alex Tan");
        policy.setSumInsured(new BigDecimal("50000.00"));
        policy.setStatus(PolicyStatus.ACTIVE);
        return policy;
    }

    public static Policy lapsedAuMotor() {
        Policy policy = activeAuMotor();
        policy.setPolicyNumber("POL-AU-MOTOR-LAPSED");
        policy.setStatus(PolicyStatus.LAPSED);
        return policy;
    }

    public static Staff officer() {
        return staff(OFFICER_ID, "Riley Chen", Market.AU, "AU-MOTOR-1", StaffRole.OFFICER);
    }

    public static Staff otherOfficer() {
        return staff(OTHER_OFFICER_ID, "Morgan Singh", Market.AU, "AU-MOTOR-1", StaffRole.OFFICER);
    }

    public static Staff manager() {
        return staff(MANAGER_ID, "Avery Brooks", Market.AU, "AU-MGMT", StaffRole.MANAGER);
    }

    public static Staff hkOfficer() {
        return staff(HK_OFFICER_ID, "Quinn Ho", Market.HK, "HK-MOTOR-1", StaffRole.OFFICER);
    }

    public static Staff staff(UUID id, String name, Market market, String team, StaffRole role) {
        Staff staff = new Staff();
        staff.setId(id);
        staff.setFullName(name);
        staff.setEmail(name.toLowerCase().replace(' ', '.') + "@chubb.example");
        staff.setMarket(market);
        staff.setTeam(team);
        staff.setRole(role);
        staff.setActive(true);
        return staff;
    }

    public static Claim openClaim() {
        return Claim.open(
                "CLM-AU-00000001",
                activeAuMotor(),
                "Pat Claimant",
                "pat.claimant@example.com",
                "+61400000000",
                LocalDate.of(2024, 6, 1),
                "Sydney",
                "Rear-end collision",
                new BigDecimal("1000.00"));
    }

    public static Claim inProgressClaim(Staff assignee) {
        Claim claim = openClaim();
        claim.assign(assignee, java.time.Instant.parse("2024-06-02T00:00:00Z"));
        return claim;
    }
}
