package com.chubb.claims.journey;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InformationRequestRoundTripIT extends JourneySupport {

    @Test
    void requestProvideThenSettle() {
        String claimNumber = submitAuMotor();
        assertThat(assign(claimNumber, OFFICER).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> requested = http.exchange(
                "/api/v1/staff/claims/" + claimNumber + "/information-requests",
                HttpMethod.POST,
                jsonWithStaff(OFFICER, Map.of("body", "Please send photos")),
                Map.class);
        assertThat(requested.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(requested.getBody().get("status")).isEqualTo("PENDING_INFORMATION");

        ResponseEntity<Map> tracked = track(claimNumber);
        assertThat(tracked.getBody().get("status")).isEqualTo("PENDING_INFORMATION");
        assertThat(tracked.getBody().get("latestInformationRequest")).isEqualTo("Please send photos");

        ResponseEntity<Map> provided = http.postForEntity(
                "/api/v1/claims/" + claimNumber + "/information",
                json(Map.of("body", "Photos attached")),
                Map.class);
        assertThat(provided.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(provided.getBody().get("status")).isEqualTo("IN_PROGRESS");

        ResponseEntity<Map> settled = http.exchange(
                "/api/v1/staff/claims/" + claimNumber + "/decision",
                HttpMethod.POST,
                jsonWithStaff(OFFICER, Map.of("type", "SETTLED", "settlementAmount", 500)),
                Map.class);
        assertThat(settled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(settled.getBody().get("status")).isEqualTo("SETTLED");
    }
}
