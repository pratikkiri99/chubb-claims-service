package com.chubb.claims.journey;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyEligibilityIT extends JourneySupport {

    @Test
    void lapsedPolicyIs422() {
        ResponseEntity<Map> response = http.postForEntity("/api/v1/claims", json(Map.of(
                "policyNumber", "POL-AU-MOTOR-LAPSED",
                "market", "AU",
                "claimantName", "Pat Claimant",
                "claimantEmail", "pat.claimant@example.com",
                "claimantPhone", "+61400000000",
                "incidentDate", "2024-06-01",
                "incidentLocation", "Sydney",
                "incidentDescription", "Crash",
                "claimedAmount", 1000
        )), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat((String) response.getBody().get("type")).startsWith("urn:chubb:claims:problem:");
    }

    @Test
    void unknownPolicyIs404() {
        ResponseEntity<Map> response = http.postForEntity("/api/v1/claims", json(Map.of(
                "policyNumber", "POL-DOES-NOT-EXIST",
                "market", "AU",
                "claimantName", "Pat Claimant",
                "claimantEmail", "pat.claimant@example.com",
                "claimantPhone", "+61400000000",
                "incidentDate", "2024-06-01",
                "incidentLocation", "Sydney",
                "incidentDescription", "Crash",
                "claimedAmount", 1000
        )), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void hkOfficerCannotAssignAuClaim() {
        String claimNumber = submitAuMotor();
        ResponseEntity<Map> response = assign(claimNumber, HK_OFFICER);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
