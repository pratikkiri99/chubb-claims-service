package com.chubb.claims.journey;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClaimantSubmitAndTrackIT extends JourneySupport {

    @Test
    void submitThenTrack() {
        ResponseEntity<Map> created = http.postForEntity("/api/v1/claims", json(Map.of(
                "policyNumber", "POL-AU-MOTOR-001",
                "market", "AU",
                "claimantName", "Pat Claimant",
                "claimantEmail", "pat.claimant@example.com",
                "claimantPhone", "+61400000000",
                "incidentDate", "2024-06-01",
                "incidentLocation", "Sydney",
                "incidentDescription", "Rear-end collision",
                "claimedAmount", 1000
        )), Map.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getHeaders().getLocation()).isNotNull();
        String claimNumber = (String) created.getBody().get("claimNumber");
        assertThat(created.getBody().get("status")).isEqualTo("OPEN");
        assertThat(created.getBody()).doesNotContainKey("assignedStaffId");
        assertThat(created.getBody()).doesNotContainKey("reserveAmount");

        ResponseEntity<Map> tracked = track(claimNumber);
        assertThat(tracked.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tracked.getBody().get("status")).isEqualTo("OPEN");
        assertThat(tracked.getBody()).doesNotContainKey("reserveAmount");
    }
}
