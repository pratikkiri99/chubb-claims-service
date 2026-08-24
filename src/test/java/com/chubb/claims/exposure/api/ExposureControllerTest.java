package com.chubb.claims.exposure.api;

import com.chubb.claims.exposure.ExposureService;
import com.chubb.claims.exposure.ExposureSnapshot;
import com.chubb.claims.shared.api.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExposureController.class)
@Import(GlobalExceptionHandler.class)
class ExposureControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExposureService exposureService;

    @Test
    void returnsExposureShape() throws Exception {
        UUID staffId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(exposureService.get(eq(staffId), any(Optional.class))).thenReturn(new ExposureSnapshot(
                Instant.parse("2024-07-01T00:00:00Z"),
                new BigDecimal("1000.00"),
                1,
                List.of(new ExposureSnapshot.Slice("AU", new BigDecimal("1000.00"), 1)),
                List.of(new ExposureSnapshot.Slice("MOTOR", new BigDecimal("1000.00"), 1))));

        mockMvc.perform(get("/api/v1/staff/exposure").header("X-Staff-Id", staffId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outstandingClaimCount").value(1))
                .andExpect(jsonPath("$.totalOutstandingReserve").value(1000.00))
                .andExpect(jsonPath("$.byMarket[0].key").value("AU"));
    }
}
