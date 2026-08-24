package com.chubb.claims.staff;

import com.chubb.claims.AbstractJpaTest;
import com.chubb.claims.shared.domain.Market;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StaffRepositoryTest extends AbstractJpaTest {

    @Autowired
    private StaffRepository staffRepository;

    @Test
    void roundTripsStaff() {
        Staff staff = newStaff("new.officer@chubb.example");

        Staff saved = staffRepository.saveAndFlush(staff);

        Staff loaded = staffRepository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getFullName()).isEqualTo("New Officer");
        assertThat(loaded.getRole()).isEqualTo(StaffRole.OFFICER);
        assertThat(staffRepository.findByIdAndActiveTrue(saved.getId())).isPresent();
        assertThat(staffRepository.findByMarketAndTeam(Market.AU, "AU-TEST")).hasSize(1);
    }

    @Test
    void rejectsDuplicateEmail() {
        staffRepository.saveAndFlush(newStaff("dup.officer@chubb.example"));

        assertThatThrownBy(() -> staffRepository.saveAndFlush(newStaff("dup.officer@chubb.example")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static Staff newStaff(String email) {
        Staff staff = new Staff();
        staff.setFullName("New Officer");
        staff.setEmail(email);
        staff.setMarket(Market.AU);
        staff.setTeam("AU-TEST");
        staff.setRole(StaffRole.OFFICER);
        staff.setActive(true);
        return staff;
    }
}
