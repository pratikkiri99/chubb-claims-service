package com.chubb.claims.journey;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrentAssignIT extends JourneySupport {

    @Test
    void onlyOneOfficerWins() throws Exception {
        String claimNumber = submitAuMotor();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<ResponseEntity<Map>> first =
                    CompletableFuture.supplyAsync(() -> assign(claimNumber, OFFICER), pool);
            CompletableFuture<ResponseEntity<Map>> second =
                    CompletableFuture.supplyAsync(() -> assign(claimNumber, OTHER_OFFICER), pool);
            CompletableFuture.allOf(first, second).join();

            int ok = 0;
            int conflict = 0;
            for (ResponseEntity<Map> response : java.util.List.of(first.get(), second.get())) {
                if (response.getStatusCode() == HttpStatus.OK) {
                    ok++;
                } else if (response.getStatusCode() == HttpStatus.CONFLICT) {
                    conflict++;
                }
            }
            assertThat(ok).isEqualTo(1);
            assertThat(conflict).isEqualTo(1);

            ResponseEntity<Map> detail = staffGet(claimNumber, MANAGER);
            assertThat(detail.getBody().get("assignedStaffId")).isIn(OFFICER, OTHER_OFFICER);
        } finally {
            pool.shutdownNow();
        }
    }
}
