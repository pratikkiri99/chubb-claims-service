package com.chubb.claims.claim.api;

import com.chubb.claims.claim.Claim;
import com.chubb.claims.claim.ClaimFixtures;
import com.chubb.claims.claim.ClaimService;
import com.chubb.claims.shared.api.GlobalExceptionHandler;
import com.chubb.claims.shared.error.ClaimNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClaimantClaimController.class)
@Import({GlobalExceptionHandler.class, ClaimMapper.class})
class ClaimantClaimControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClaimService claimService;

    @Test
    void submitReturns201AndLocation() throws Exception {
        Claim claim = stamped(ClaimFixtures.openClaim());
        when(claimService.submit(any())).thenReturn(claim);

        mockMvc.perform(post("/api/v1/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "policyNumber": "POL-AU-MOTOR-001",
                                  "market": "AU",
                                  "claimantName": "Pat Claimant",
                                  "claimantEmail": "pat.claimant@example.com",
                                  "claimantPhone": "+61400000000",
                                  "incidentDate": "2024-06-01",
                                  "incidentLocation": "Sydney",
                                  "incidentDescription": "Rear-end collision",
                                  "claimedAmount": 1000.00
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/claims/CLM-AU-00000001"))
                .andExpect(jsonPath("$.claimNumber").value("CLM-AU-00000001"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.reserveAmount").doesNotExist());
    }

    @Test
    void submitInvalidBodyReturnsProblemDetail() throws Exception {
        mockMvc.perform(post("/api/v1/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "policyNumber": "",
                                  "market": "AU",
                                  "claimantName": "Pat",
                                  "claimantEmail": "not-an-email",
                                  "claimantPhone": "+61400000000",
                                  "incidentDate": "2024-06-01",
                                  "incidentLocation": "Sydney",
                                  "incidentDescription": "Crash",
                                  "claimedAmount": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:chubb:claims:problem:validation"))
                .andExpect(jsonPath("$.title").exists())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").exists())
                .andExpect(jsonPath("$.instance").value("/api/v1/claims"));
    }

    @Test
    void trackOmitsReserveAmount() throws Exception {
        when(claimService.getByClaimNumber("CLM-AU-00000001")).thenReturn(stamped(ClaimFixtures.openClaim()));

        mockMvc.perform(get("/api/v1/claims/CLM-AU-00000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimNumber").value("CLM-AU-00000001"))
                .andExpect(jsonPath("$.reserveAmount").doesNotExist());
    }

    @Test
    void trackUnknownReturns404ProblemDetail() throws Exception {
        when(claimService.getByClaimNumber("CLM-AU-99999999"))
                .thenThrow(new ClaimNotFoundException("CLM-AU-99999999"));

        mockMvc.perform(get("/api/v1/claims/CLM-AU-99999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:chubb:claims:problem:claim-not-found"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void provideInformationReturns200() throws Exception {
        when(claimService.provideInformation("CLM-AU-00000001", "Photos"))
                .thenReturn(stamped(ClaimFixtures.openClaim()));

        mockMvc.perform(post("/api/v1/claims/CLM-AU-00000001/information")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "body": "Photos" }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void unexpectedErrorHidesInternalMessage() throws Exception {
        when(claimService.getByClaimNumber("CLM-AU-00000001"))
                .thenThrow(new RuntimeException("secret"));

        mockMvc.perform(get("/api/v1/claims/CLM-AU-00000001"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.detail", not(containsString("secret"))));
    }

    private static Claim stamped(Claim claim) {
        Instant now = Instant.parse("2024-06-02T00:00:00Z");
        claim.setCreatedAt(now);
        claim.setUpdatedAt(now);
        return claim;
    }
}
