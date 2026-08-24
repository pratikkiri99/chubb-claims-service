package com.chubb.claims.workload.api;

import com.chubb.claims.claim.ClaimStatus;
import com.chubb.claims.shared.api.GlobalExceptionHandler;
import com.chubb.claims.shared.domain.Market;
import com.chubb.claims.staff.StaffRole;
import com.chubb.claims.workload.WorkloadService;
import com.chubb.claims.workload.WorkloadSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WorkloadController.class)
@Import(GlobalExceptionHandler.class)
class WorkloadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WorkloadService workloadService;

    @Test
    void returnsWorkloadShape() throws Exception {
        UUID staffId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Map<ClaimStatus, Long> counts = new EnumMap<>(ClaimStatus.class);
        counts.put(ClaimStatus.OPEN, 1L);
        when(workloadService.get(any())).thenReturn(new WorkloadSnapshot(
                staffId, "Riley Chen", StaffRole.OFFICER, Market.AU, 1,
                List.of(), List.of(), counts,
                new WorkloadSnapshot.PerformanceSnapshot(0, 0, null)));

        mockMvc.perform(get("/api/v1/staff/workload").header("X-Staff-Id", staffId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incomingQueueCount").value(1))
                .andExpect(jsonPath("$.staffName").value("Riley Chen"))
                .andExpect(jsonPath("$.performance.settledCount").value(0));
    }
}
