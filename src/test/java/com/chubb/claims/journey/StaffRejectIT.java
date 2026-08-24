package com.chubb.claims.journey;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StaffRejectIT extends JourneySupport {

    @Test
    void assignThenReject() {
        String claimNumber = submitAuMotor();
        assertThat(assign(claimNumber, OFFICER).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> rejected = http.exchange(
                "/api/v1/staff/claims/" + claimNumber + "/decision",
                HttpMethod.POST,
                jsonWithStaff(OFFICER, Map.of("type", "REJECTED", "rejectionReason", "Outside cover")),
                Map.class);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> tracked = track(claimNumber);
        assertThat(tracked.getBody().get("status")).isEqualTo("REJECTED");
        assertThat(tracked.getBody().get("rejectionReason")).isEqualTo("Outside cover");
        assertThat(tracked.getBody().get("settlementAmount")).isNull();
    }
}
