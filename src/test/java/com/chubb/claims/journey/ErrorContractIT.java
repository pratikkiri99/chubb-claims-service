package com.chubb.claims.journey;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorContractIT extends JourneySupport {

    @Test
    void unknownClaimIs404Problem() {
        ResponseEntity<Map> response = track("CLM-AU-99999999");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat((String) response.getBody().get("type")).startsWith("urn:chubb:claims:problem:");
    }

    @Test
    void informationOnOpenIs409() {
        String claimNumber = submitAuMotor();
        ResponseEntity<Map> response = http.postForEntity(
                "/api/v1/claims/" + claimNumber + "/information",
                json(Map.of("body", "Photos")),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat((String) response.getBody().get("type")).startsWith("urn:chubb:claims:problem:");
    }

    @Test
    void decisionOnOpenIs409() {
        String claimNumber = submitAuMotor();
        ResponseEntity<Map> response = http.exchange(
                "/api/v1/staff/claims/" + claimNumber + "/decision",
                HttpMethod.POST,
                jsonWithStaff(MANAGER, Map.of("type", "SETTLED", "settlementAmount", 100)),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void missingStaffHeaderIs401() {
        ResponseEntity<Map> response = http.getForEntity("/api/v1/staff/claims/queue", Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat((String) response.getBody().get("type")).startsWith("urn:chubb:claims:problem:");
    }

    @Test
    void openApiDocsAreAvailable() {
        ResponseEntity<String> docs = http.getForEntity("/v3/api-docs", String.class);
        assertThat(docs.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(docs.getBody()).contains("Chubb Claims Service");
    }
}
