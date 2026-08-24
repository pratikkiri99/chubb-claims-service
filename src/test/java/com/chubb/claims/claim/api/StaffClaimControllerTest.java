package com.chubb.claims.claim.api;

import com.chubb.claims.claim.Claim;
import com.chubb.claims.claim.ClaimFixtures;
import com.chubb.claims.claim.ClaimService;
import com.chubb.claims.shared.api.GlobalExceptionHandler;
import com.chubb.claims.shared.error.ClaimAlreadyAssignedException;
import com.chubb.claims.shared.error.StaffForbiddenException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StaffClaimController.class)
@Import({GlobalExceptionHandler.class, ClaimMapper.class})
class StaffClaimControllerTest {

    private static final String STAFF = "11111111-1111-1111-1111-111111111111";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClaimService claimService;

    @Test
    void missingStaffHeaderIs401() throws Exception {
        mockMvc.perform(get("/api/v1/staff/claims/queue"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:chubb:claims:problem:staff-unauthorized"));
    }

    @Test
    void queueReturns200() throws Exception {
        Claim claim = stamped(ClaimFixtures.openClaim());
        when(claimService.incomingQueue(UUID.fromString(STAFF))).thenReturn(List.of(claim));

        mockMvc.perform(get("/api/v1/staff/claims/queue").header("X-Staff-Id", STAFF))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].claimNumber").value("CLM-AU-00000001"));
    }

    @Test
    void assignReturns200() throws Exception {
        Claim assigned = stamped(ClaimFixtures.inProgressClaim(ClaimFixtures.officer()));
        assigned.setId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        when(claimService.assignToSelf(eq("CLM-AU-00000001"), any())).thenReturn(assigned);

        mockMvc.perform(post("/api/v1/staff/claims/CLM-AU-00000001/assignment")
                        .header("X-Staff-Id", STAFF))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.reserveAmount").exists());
    }

    @Test
    void assignAlreadyAssignedReturns409() throws Exception {
        when(claimService.assignToSelf(eq("CLM-AU-00000001"), any()))
                .thenThrow(new ClaimAlreadyAssignedException("CLM-AU-00000001"));

        mockMvc.perform(post("/api/v1/staff/claims/CLM-AU-00000001/assignment")
                        .header("X-Staff-Id", STAFF))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:chubb:claims:problem:claim-already-assigned"));
    }

    @Test
    void decisionSettledWithoutAmountIs400() throws Exception {
        mockMvc.perform(post("/api/v1/staff/claims/CLM-AU-00000001/decision")
                        .header("X-Staff-Id", STAFF)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "type": "SETTLED" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:chubb:claims:problem:validation"));
    }

    @Test
    void marketForbiddenReturns403() throws Exception {
        when(claimService.assignToSelf(eq("CLM-AU-00000001"), any()))
                .thenThrow(new StaffForbiddenException("Staff market does not match claim market"));

        mockMvc.perform(post("/api/v1/staff/claims/CLM-AU-00000001/assignment")
                        .header("X-Staff-Id", "44444444-4444-4444-4444-444444444444"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:chubb:claims:problem:staff-forbidden"));
    }

    private static Claim stamped(Claim claim) {
        Instant now = Instant.parse("2024-06-02T00:00:00Z");
        claim.setCreatedAt(now);
        claim.setUpdatedAt(now);
        return claim;
    }
}
