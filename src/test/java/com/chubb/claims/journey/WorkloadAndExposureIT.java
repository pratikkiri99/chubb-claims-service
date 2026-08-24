package com.chubb.claims.journey;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WorkloadAndExposureIT extends JourneySupport {

    @Test
    void exposureExcludesSettledAndWorkloadCountsQueue() {
        String openOne = submit("POL-AU-MOTOR-001", "AU", new BigDecimal("1000.00"));
        String openTwo = submit("POL-AU-MOTOR-001", "AU", new BigDecimal("2000.00"));
        String toSettle = submit("POL-AU-MOTOR-001", "AU", new BigDecimal("500.00"));

        assertThat(assign(toSettle, OFFICER).getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<Map> settled = http.exchange(
                "/api/v1/staff/claims/" + toSettle + "/decision",
                HttpMethod.POST,
                jsonWithStaff(OFFICER, Map.of("type", "SETTLED", "settlementAmount", 500)),
                Map.class);
        assertThat(settled.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> exposure = http.exchange(
                "/api/v1/staff/exposure", HttpMethod.GET, staff(OFFICER), Map.class);
        assertThat(exposure.getStatusCode()).isEqualTo(HttpStatus.OK);
        Number total = (Number) exposure.getBody().get("totalOutstandingReserve");
        Number count = (Number) exposure.getBody().get("outstandingClaimCount");
        assertThat(total.doubleValue()).isGreaterThanOrEqualTo(3000.0);
        assertThat(count.intValue()).isGreaterThanOrEqualTo(2);

        ResponseEntity<Map> workload = http.exchange(
                "/api/v1/staff/workload", HttpMethod.GET, staff(OFFICER), Map.class);
        assertThat(workload.getStatusCode()).isEqualTo(HttpStatus.OK);
        Number queue = (Number) workload.getBody().get("incomingQueueCount");
        assertThat(queue.intValue()).isGreaterThanOrEqualTo(2);
        @SuppressWarnings("unchecked")
        Map<String, Object> performance = (Map<String, Object>) workload.getBody().get("performance");
        Number settledCount = (Number) performance.get("settledCount");
        assertThat(settledCount.intValue()).isGreaterThanOrEqualTo(1);

        assertThat(openOne).isNotBlank();
        assertThat(openTwo).isNotBlank();
    }
}
