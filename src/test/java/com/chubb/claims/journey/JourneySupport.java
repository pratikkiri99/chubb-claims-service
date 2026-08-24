package com.chubb.claims.journey;

import com.chubb.claims.AbstractPostgresIT;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class JourneySupport extends AbstractPostgresIT {

    protected static final String OFFICER = "11111111-1111-1111-1111-111111111111";
    protected static final String OTHER_OFFICER = "22222222-2222-2222-2222-222222222222";
    protected static final String MANAGER = "33333333-3333-3333-3333-333333333333";
    protected static final String HK_OFFICER = "44444444-4444-4444-4444-444444444444";

    private static final AtomicInteger DISTINCT = new AtomicInteger();

    @Autowired
    protected TestRestTemplate http;

    protected String submitAuMotor() {
        return submit("POL-AU-MOTOR-001", "AU", new BigDecimal("1000.00"));
    }

    protected String submit(String policyNumber, String market, BigDecimal claimedAmount) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("policyNumber", policyNumber);
        body.put("market", market);
        body.put("claimantName", "Pat Claimant");
        body.put("claimantEmail", "pat.claimant@example.com");
        body.put("claimantPhone", "+61400000000");
        body.put("incidentDate", "2024-06-01");
        body.put("incidentLocation", "Sydney " + DISTINCT.incrementAndGet());
        body.put("incidentDescription", "Rear-end collision");
        body.put("claimedAmount", claimedAmount);

        ResponseEntity<Map> response = http.postForEntity("/api/v1/claims", json(body), Map.class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("Submit failed: " + response.getStatusCode() + " " + response.getBody());
        }
        return (String) response.getBody().get("claimNumber");
    }

    protected ResponseEntity<Map> track(String claimNumber) {
        return http.exchange("/api/v1/claims/" + claimNumber, HttpMethod.GET, HttpEntity.EMPTY, Map.class);
    }

    protected ResponseEntity<Map> staffGet(String claimNumber, String staffId) {
        return http.exchange(
                "/api/v1/staff/claims/" + claimNumber, HttpMethod.GET, staff(staffId), Map.class);
    }

    protected ResponseEntity<Map> assign(String claimNumber, String staffId) {
        return http.exchange(
                "/api/v1/staff/claims/" + claimNumber + "/assignment",
                HttpMethod.POST, staff(staffId), Map.class);
    }

    protected HttpEntity<Void> staff(String staffId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Staff-Id", staffId);
        return new HttpEntity<>(headers);
    }

    protected HttpEntity<Map<String, Object>> json(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    protected HttpEntity<Map<String, Object>> jsonWithStaff(String staffId, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Staff-Id", staffId);
        return new HttpEntity<>(body, headers);
    }

    protected UUID staffUuid(String staffId) {
        return UUID.fromString(staffId);
    }
}
