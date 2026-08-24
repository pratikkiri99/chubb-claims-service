package com.chubb.claims.journey;

import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StaffPickupAssessSettleIT extends JourneySupport {

    @Test
    void pickupAssessAndSettle() {
        String claimNumber = submitAuMotor();

        ResponseEntity<List<Map<String, Object>>> queue = http.exchange(
                "/api/v1/staff/claims/queue", HttpMethod.GET, staff(OFFICER),
                new ParameterizedTypeReference<>() {});
        assertThat(queue.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(queue.getBody()).extracting(row -> row.get("claimNumber")).contains(claimNumber);

        ResponseEntity<Map> assigned = assign(claimNumber, OFFICER);
        assertThat(assigned.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(assigned.getBody().get("status")).isEqualTo("IN_PROGRESS");
        assertThat(assigned.getBody().get("assignedStaffId")).isEqualTo(OFFICER);

        ResponseEntity<List<Map<String, Object>>> after = http.exchange(
                "/api/v1/staff/claims/queue", HttpMethod.GET, staff(OFFICER),
                new ParameterizedTypeReference<>() {});
        assertThat(after.getBody()).extracting(row -> row.get("claimNumber")).doesNotContain(claimNumber);

        ResponseEntity<Map> reserved = http.exchange(
                "/api/v1/staff/claims/" + claimNumber + "/reserve",
                HttpMethod.PATCH,
                jsonWithStaff(OFFICER, Map.of("reserveAmount", new BigDecimal("800.00"))),
                Map.class);
        assertThat(reserved.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> detail = staffGet(claimNumber, OFFICER);
        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(detail.getBody().get("reserveAmount")).isEqualTo(800.0);

        ResponseEntity<Map> settled = http.exchange(
                "/api/v1/staff/claims/" + claimNumber + "/decision",
                HttpMethod.POST,
                jsonWithStaff(OFFICER, Map.of("type", "SETTLED", "settlementAmount", 750)),
                Map.class);
        assertThat(settled.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> tracked = track(claimNumber);
        assertThat(tracked.getBody().get("status")).isEqualTo("SETTLED");
        assertThat(((Number) tracked.getBody().get("settlementAmount")).doubleValue()).isEqualTo(750.0);
    }
}
